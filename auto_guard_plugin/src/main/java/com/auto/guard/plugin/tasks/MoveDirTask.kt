package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.aidlDirs
import com.auto.guard.plugin.utils.allDependencyAndroidProjects
import com.auto.guard.plugin.utils.findPackage
import com.auto.guard.plugin.utils.findXmlDirs
import com.auto.guard.plugin.utils.generateMoveDirMap
import com.auto.guard.plugin.utils.getDirPath
import com.auto.guard.plugin.utils.getExcludeClassPathList
import com.auto.guard.plugin.utils.insertImportXxxIfAbsent
import com.auto.guard.plugin.utils.manifestFile
import com.auto.guard.plugin.utils.replaceWords
import com.auto.guard.plugin.utils.resDirs
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class MoveDirTask @Inject constructor(
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
            moveDirMap = project.generateMoveDirMap(pluginParams, variantName)
        ).execute()
    }

    class Proxy(
        val task: DefaultTask,
        val project: Project,
        val pluginParams: AutoGuardExtension,
        val variantName: String,
        val moveDirMap: Map<String, String>
    ) {

        companion object {
            private val PACKAGE_REGEX = Pair(
                """\s*package\s+([\w.]+)\s*;?""", 1
            )
            private val CLZ_OR_OBJECT_NAME_REGEX = Pair(
                """\s*(class|object|interface)\s+(\w+)\s*(\(|\{)?""", 2
            )
            private val MTD_NAME_REGEX =
                """\s*(fun|((val|var)(\s+\@receiver\:\w+)?))\s+(<[^<>]+>\s+)?(\w+(<[^<>]+>)?\.)?(\w+)"""
        }

        private val excludeClzPathList = task.getExcludeClassPathList(pluginParams, variantName)
        private val excludeClzFolderPathList = excludeClzPathList.map { it.getDirPath() }.distinct()

        fun execute() {
            if (moveDirMap.isEmpty()) return
            val androidProjects = task.allDependencyAndroidProjects()
            val validMoveDirMap = moveDirMap.filter {
                it.key !in excludeClzFolderPathList
            }
            androidProjects.forEach { it.moveDir(validMoveDirMap) }
            fixPackageAndImports()
        }

        private fun Project.moveDir(moveFile: Map<String, String>) {
            val manifestPackage = findPackage()  //查找清单文件里的package属性值
            //1、替换manifest文件 、layout、navigation、xml目录下的文件、Java、Kt文件
            val dirs = findXmlDirs(variantName, "layout", "navigation", "xml")
            dirs.add(manifestFile())
            val aidlDirs = aidlDirs(variantName)
            dirs.addAll(aidlDirs)
            val javaDirs = sourceSetDirs(variantName)
            dirs.addAll(javaDirs)
            val moveDirs = mutableListOf<File>()
            moveDirs.addAll(aidlDirs)
            moveDirs.addAll(javaDirs)
            files(dirs).asFileTree
                .sortedByDescending { it.absolutePath.count { it == File.separatorChar } }
                .forEach {
                    it.replaceText(moveFile, manifestPackage)
                }

            // 2、开始移动目录
            moveDirs.forEach moveDirs@{ javaSourceDir ->
                moveFile.toList()
                    .sortedByDescending { it.first.count { it == '.' } }
                    .forEach { (oldPath, newPath) ->
                        val oldPackagePath = oldPath.replace(".", File.separator)
                        if ("${oldPath}.${javaSourceDir.nameWithoutExtension}" in excludeClzPathList) {
                            // excluded by user config
                            return@forEach
                        }
                        val oldDir = File(javaSourceDir, oldPackagePath)
                        if (!oldDir.exists()) {
                            return@forEach
                        }
                        if (oldPath == manifestPackage) {
                            //包名目录下的直接子类移动位置，需要重新手动导入R类及BuildConfig类(如果有用到的话)
                            oldDir.listFiles { f -> !f.isDirectory }?.forEach { file ->
                                file.insertImportXxxIfAbsent(oldPath)
                            }
                        }
                        val oldRelativePath = oldPath.replace(".", File.separator)
                        val newRelativePath = newPath.replace(".", File.separator)
                        println("move pkg ${oldRelativePath} to ${newRelativePath}")
                        oldDir.allFiles().forEach {
                            val toPath = it.absolutePath.replace(
                                "${File.separator}$oldRelativePath${File.separator}",
                                "${File.separator}$newRelativePath${File.separator}"
                            )
                            val toFilename = File(toPath)
                            if (!toFilename.exists()) toFilename.parentFile.mkdirs()
                            it.renameTo(toFilename)

                            println(
                                "move file:" +
                                        "${it.relativeTo(javaSourceDir).path}" +
                                        " -> " +
                                        "${toFilename.relativeTo(javaSourceDir).path}"
                            )
                        }
                    }
            }
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

            // 提取现有的package语句
            val oldPackageName = Regex(
                PACKAGE_REGEX.first,
                RegexOption.MULTILINE
            ).find(content)?.groups?.get(PACKAGE_REGEX.second)?.value?.trim()
                ?.replace(";", "")
                ?.trim()

            // 提取类名或对象名（假设每个文件只包含一个顶级类或对象）
            val clzOrObjNames = Regex(
                CLZ_OR_OBJECT_NAME_REGEX.first,
                RegexOption.MULTILINE
            ).findAll(content).map {
                it.groups[CLZ_OR_OBJECT_NAME_REGEX.second]?.value?.trim()
                    ?.let result@{ str ->
                        str.indexOf(":").takeIf { it >= 0 }?.let {
                            return@result str.substring(0, it)
                        }
                        str.indexOf("{").takeIf { it >= 0 }?.let {
                            return@result str.substring(0, it)
                        }
                        str.indexOf("(").takeIf { it >= 0 }?.let {
                            return@result str.substring(0, it)
                        }
                        return@result str
                    }?.trim()
            }.filterNotNull()
                .toList()

            // 提取函数名（假设每个文件只包含一个顶级类或对象）
            val mtdNames = Regex(
                MTD_NAME_REGEX,
                RegexOption.MULTILINE
            ).findAll(content).map {
                it.groups.lastOrNull()?.value?.trim()
                    ?.let result@{ str ->
                        str.indexOf("(").takeIf { it >= 0 }?.let {
                            return@result str.substring(0, it)
                        }
                        return@result str
                    }?.trim()
            }.filterNotNull()
                .toList()

            println(
                "fix clz:\n\toldPackageName:${oldPackageName}" +
                        "\n\tnewPackageName:${newPackageName}" +
                        "\n\tclzOrObjNames:${clzOrObjNames.joinToString(", ")}" +
                        "\n\tmtdNames:${mtdNames.joinToString(", ")}"
            )
            return listOf(oldPackageName, newPackageName, clzOrObjNames, mtdNames)
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
            val newPackageDeclaration = if (newPkg.isNotEmpty()) "package $newPkg;" else ""

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

        private fun File.allFiles(): List<File> {
            val files = mutableListOf<File>()
            listFiles()?.forEach {
                if (it.isDirectory) {
                    files.addAll(it.allFiles())
                } else {
                    files.add(it)
                }
            }
            return files
        }

        private fun File.replaceText(map: Map<String, String>, manifestPackage: String?) {
            var replaceText = readText()
            map.forEach { (oldPath, newPath) ->
                // println("replace ${oldPath} to ${newPath}")
                replaceText =
                    if (name == "AndroidManifest.xml" && oldPath == manifestPackage) {
                        replaceText.replaceWords("$oldPath.", "$newPath.")
                            .replaceWords("""android:name=".""", """android:name="${newPath}.""")
                    } else {
                        replaceText.replaceWords(oldPath, newPath)
                    }
                if (name.endsWith(".kt") || name.endsWith(".java")) {
                    /*
                     移动目录时，manifest里的package属性不会更改
                     上面代码会将将R、BuildConfig等类路径替换掉，所以这里需要还原回去
                     */
                    replaceText = replaceText.replaceWords("$newPath.R", "$oldPath.R")
                        .replaceWords("$newPath.BR", "$oldPath.BR")
                        .replaceWords("$newPath.BuildConfig", "$oldPath.BuildConfig")
                        .replaceWords("$newPath.databinding", "$oldPath.databinding")
                }
            }
            writeText(replaceText)
        }
    }

}