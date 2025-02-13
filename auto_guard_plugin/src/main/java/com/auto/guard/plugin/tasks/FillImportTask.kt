package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class FillImportTask @Inject constructor(
    private val pluginParams: AutoGuardExtension,
    private val variantName: String,
) : DefaultTask() {

    init {
        group = "guard"
    }

    @TaskAction
    fun execute() {
        Proxy(
            this,
            project,
            pluginParams,
            variantName,
        ).execute()
    }

    class Proxy(
        val task: DefaultTask,
        val project: Project,
        val pluginParams: AutoGuardExtension,
        val variantName: String,
    ) {
        fun execute() {
            val codeDirs = project.sourceSetDirs(variantName)
            val codeFiles = codeDirs.flatMap { sourceRoot ->
                sourceRoot.walkTopDown()
                    .filter {
                        it.isFile && (it.extension == "java" || it.extension == "kt")
                    }
                    .distinct()
                    .toList()
            }
            codeFiles.forEach { file ->
                processFile(file, codeFiles)
            }
        }

        fun extractClasses(file: File): List<Pair<String?, String>> {
            val content = file.readText()
            val classOrObjectRegex = "\\b(?:class|object)\\s+(\\w+)\\s*\\{?".toRegex()

            val stack = mutableListOf<String>() // 用于追踪嵌套容器
            val result = mutableListOf<Pair<String?, String>>() // 存储容器和类名的关系

            content.lines().forEach { line ->
                // 检查大括号打开
                val openBracesNum = line.count { it == '{' } - line.count { it == '}' }
                if (openBracesNum > 0) {

                    val match = classOrObjectRegex.find(line)
                    if (match != null) {
                        val name = match.groupValues[1] // 获取类名或对象名
                        val container = stack.filter { it != "" }.joinToString(".") // 获取当前栈顶的容器
                        result.add(container to name) // 记录关系
                        stack.add(name) // 当前类名或对象名入栈
                        repeat(openBracesNum - 1) {
                            stack.add("") // 占位符，表示当前层次嵌套
                        }
                    } else {
                        repeat(openBracesNum) {
                            stack.add("") // 占位符，表示当前层次嵌套
                        }
                    }
                } else if (openBracesNum < 0) {
                    repeat(-openBracesNum) {
                        stack.removeLast() // 占位符，表示当前层次嵌套
                    }
                }
            }

            return result.filterNot { it.second.isEmpty() } // 去除占位符结果
        }

        fun findMissingImports(
            file: File,
            siblingClasses: List<Pair<String?, String>>,
            packageName: String?
        ): List<String> {
            if (packageName == null) return emptyList()

            val content = file.readText()
            val importRegex = "^import\\s+([\\w.]+);?".toRegex(RegexOption.MULTILINE)
            val importedClasses = importRegex.findAll(content).map { it.groupValues[1] }.toSet()

            val importedTopLevelClasses = importedClasses.map { it.split(".").last() }.toSet()

            return siblingClasses.filter { (container, cls) ->
                val qualifiedName = if (container != null) "$container.$cls" else cls
                val directReference = cls in content
                val qualifiedReference = qualifiedName in content
                (directReference || qualifiedReference) && qualifiedName !in importedClasses
            }.map { (container, cls) ->
                if (container != null) "import $packageName.$container.$cls;" else "import $packageName.$cls;"
            }.map {
                it.replace("..", ".")
            }.distinct()
        }

        fun addMissingImports(file: File, missingImports: List<String>) {
            val lines = file.readLines().toMutableList()
            val packageIndex = lines.indexOfFirst { it.trim().startsWith("package") }

            if (packageIndex != -1) {
                lines.add(packageIndex + 1, missingImports.joinToString("\n"))
            }

            file.writeText(lines.joinToString("\n"))
        }

        fun getPackageName(file: File): String? {
            val content = file.readText()
            val packageRegex = "^package\\s+([\\w.]+);?".toRegex(RegexOption.MULTILINE)
            return packageRegex.find(content)?.groupValues?.get(1)
        }

        fun processFile(file: File, allFiles: List<File>) {
            val siblingFiles = allFiles.filter { it.parent == file.parent && it != file }
            val siblingClasses = siblingFiles.flatMap { extractClasses(it) }
            val packageName = getPackageName(file)

            val missingImports = findMissingImports(file, siblingClasses, packageName)
            if (missingImports.isNotEmpty()) {
                addMissingImports(file, missingImports)
            }
        }
    }

}