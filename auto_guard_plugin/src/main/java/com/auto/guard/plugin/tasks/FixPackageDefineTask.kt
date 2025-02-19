package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.resDirs
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File
import javax.inject.Inject

open class FixPackageDefineTask @Inject constructor(
    val pluginParams: AutoGuardExtension,
    val variantName: String,
) : DefaultTask() {

    init {
        group = "guard"
    }

    @TaskAction
    fun execute() {
        execute()
        Proxy(this, project, pluginParams, variantName).execute()
    }

    class Proxy(
        val task: DefaultTask,
        val project: Project,
        val pluginParams: AutoGuardExtension,
        val variantName: String,
    ) {

        companion object {
            private val PACKAGE_REGEX = Pair(
                """\s*package\s+([\w.]+)\s*;?""", 1
            )
        }

        fun execute() {
            fixPackageAndImports()
        }

        private fun fixPackageAndImports() {
            val codeDirs = task.project.sourceSetDirs(variantName)

            val allResFiles = task.project.resDirs(variantName).flatMap {
                it.walkTopDown().filter { file -> file.isFile }.toList()
            }.sortedByDescending { it.name.length }

            codeDirs.forEach { codeDir ->
                val codeFileTree = task.project.files(codeDir).asFileTree
                codeFileTree.forEach { codeFile ->
                    codeFile.getPackageChange(codeDir).let { (oldPkg, newPkg, clzNames, mtdNames) ->
                        oldPkg as String?
                        newPkg as String
                        clzNames as List<String>
                        mtdNames as List<String>
                        if (oldPkg == newPkg) {
                            return@forEach
                        }
                        codeFile.updatePackage(oldPkg, newPkg)
                        codeFileTree.forEach { eachCodeFile ->
                            eachCodeFile.updateImports(
                                oldPkg, newPkg, clzNames, mtdNames, codeFile.name
                            )
                        }
                        allResFiles.forEach { resFile ->
                            if (resFile.extension == "xml") {
                                resFile.updateImports(
                                    oldPkg, newPkg, clzNames, mtdNames, codeFile.name
                                )
                            }
                        }
                    }
                }
            }
        }

        /**
         * 更新移动后文件的package变化映射
         * return listOf(代码中现有的包路径, 真实包路径, class/object名列表，顶级函数名列表)
         */
        fun File.getPackageChange(javaSourceDir: File): List<Any?> {
            // 获取文件相对于javaSourceDir的相对路径并生成新的包名
            val relativePath =
                this.relativeTo(javaSourceDir).parent?.replace(File.separatorChar, '.') ?: ""
            val newPackageName = if (relativePath.isNotEmpty()) relativePath else ""

            // 读取文件内容
            val content = this.readText()

            // 提取现有的packageName
            val oldPackageName = Regex(
                PACKAGE_REGEX.first,
                RegexOption.MULTILINE
            ).find(content)?.groups?.get(PACKAGE_REGEX.second)?.value?.trim()
                ?.replace(";", "")
                ?.trim()

            // 提取 类名、函数名、变量名
            val (clzOrObjNames, functions, variables) = extractIdentifiers(content)

            val mtdNames = listOf(functions, variables).flatten()

            println(
                "fix clz:\n\toldPackageName:${oldPackageName}" +
                        "\n\tnewPackageName:${newPackageName}" +
                        "\n\tclzOrObjNames:${clzOrObjNames.joinToString(", ")}" +
                        "\n\tmtdNames:${mtdNames.joinToString(", ")}"
            )
            return listOf(oldPackageName, newPackageName, clzOrObjNames, mtdNames)
        }

        fun extractIdentifiers(content: String): Triple<List<String>, List<String>, List<String>> {
            val disposable = Disposer.newDisposable()

            val configuration = CompilerConfiguration()
            configuration.put(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                org.jetbrains.kotlin.cli.common.messages.MessageCollector.NONE
            )

            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            try {
                val psiFile: KtFile = KtPsiFactory(environment.project).createFile(content)
                val classes = psiFile.collectDescendantsOfType<KtClassOrObject>().mapNotNull { it.name }
                val functions = psiFile.collectDescendantsOfType<KtNamedFunction>().mapNotNull { it.name }
                val variables = psiFile.collectDescendantsOfType<KtProperty>().mapNotNull { it.name }
                return Triple(classes, functions, variables)
            } finally {
                Disposer.dispose(disposable)
            }
        }

        /**
         * 更新文件的package语句。
         */
        fun File.updatePackage(oldPkg: String?, newPkg: String) {
            // 检查文件是否存在且为普通文件
            if (!this.exists() || !this.isFile) {
                throw IllegalArgumentException("File does not exist or is not a regular file.")
            }

            // 生成新的package声明
            val newPackageDeclaration = if (newPkg.isNotEmpty()) "package $newPkg;\n" else ""

            // 读取文件内容
            val content = this.readText()

            // 替换package语句
            val packageRegex = Regex(PACKAGE_REGEX.first, RegexOption.MULTILINE)
            val updatedContent = if (oldPkg != null) {
                // 替换旧的package声明
                content.replaceFirst(packageRegex, newPackageDeclaration)
            } else {
                // 如果没有旧的package声明，直接在文件开头插入新的声明
                if (newPackageDeclaration.isNotEmpty()) "$newPackageDeclaration\n\n$content" else content
            }

            // 写回文件
            this.writeText(updatedContent)
        }

        /**
         * 更新文件的import语句。
         */
        fun File.updateImports(
            oldPkg: String?,
            newPkg: String,
            clzNames: List<String>,
            mtdNames: List<String>,
            codeFileName: String,
        ) {
            // 检查文件是否存在且为普通文件
            if (!this.exists() || !this.isFile) {
                throw IllegalArgumentException("File does not exist or is not a regular file.")
            }

            // 读取文件内容
            val content = this.readText()

            var newContent = content
            clzNames.forEach { clzName ->
                newContent = newContent.replace("${oldPkg}.${clzName}", "${newPkg}.${clzName}")
            }
            mtdNames.forEach { mtdName ->
                newContent = newContent.replace("${oldPkg}.${mtdName}", "${newPkg}.${mtdName}")
            }
            newContent = newContent.replace("${oldPkg}.*", "${newPkg}.*")
            if (extension == "java" && codeFileName.endsWith(".kt")) {
                newContent = newContent.replace(
                    "${oldPkg}.${codeFileName.removeSuffix(".kt")}Kt",
                    "${newPkg}.${codeFileName.removeSuffix(".kt")}Kt"
                )
            }

            // 写回文件
            this.writeText(newContent)
        }
    }
}