package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.allDependencyAndroidProjects
import com.auto.guard.plugin.utils.findPackage
import com.auto.guard.plugin.utils.findXmlDirs
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

open class PackageChangeTask @Inject constructor(
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
            private val CLZ_OR_OBJECT_NAME_REGEX = Pair(
                """\s*(class|object|interface)\s+(\w+)\s*(\(|\{)?""", 2
            )
            private val MTD_NAME_REGEX =
                """\s*(fun|((val|var)(\s+\@receiver\:\w+)?))\s+(<[^<>]+>\s+)?(\w+(<[^<>]+>)?\.)?(\w+)"""
        }

        fun execute() {
            val packageExtension = pluginParams.packageChange ?: emptyMap()
            if (packageExtension.isEmpty()) return
            val androidProjects = task.allDependencyAndroidProjects()
            androidProjects.forEach { it.changePackage(packageExtension) }
            fixPackageAndImports()
        }

        private fun Project.changePackage(map: Map<String, String>) {
            val oldPackage: String = findPackage()
            val newPackage = map[oldPackage] ?: return
            val dirs = findXmlDirs(variantName, "layout")
            dirs.add(manifestFile())
            dirs.add(buildFile)
            //1、修改layout文件、AndroidManifest文件、build.gradle文件
            files(dirs).asFileTree.forEach { file ->
                when (file.name) {
                    //修改AndroidManifest.xml文件
                    "AndroidManifest.xml" -> file.modifyManifestFile(oldPackage, newPackage)
                    //修改 build.gradle namespace
                    buildFile.name -> file.modifyBuildGradleFile(oldPackage, newPackage)
                    //修改layout文件
                    else -> file.modifyLayoutXml(oldPackage)
                }
            }

            val javaDirs = sourceSetDirs(variantName)
            //2.修改 kt/java文件
            files(javaDirs).asFileTree.forEach { javaFile ->
                javaFile.readText()
                    .replaceWords("$oldPackage.R", "$newPackage.R")
                    .replaceWords("$oldPackage.BR", "$newPackage.BR")
                    .replaceWords("$oldPackage.BuildConfig", "$newPackage.BuildConfig")
                    .replaceWords("$oldPackage.databinding", "$newPackage.databinding")
                    .let { javaFile.writeText(it) }
            }

            //3.对旧包名下的直接子类，检测R类、BuildConfig类是否有用到，有的话，插入import语句
            val oldPackagePath = oldPackage.replace(".", File.separator)
            javaDirs.forEach {
                File(it, oldPackagePath).listFiles { f -> f.isFile }?.forEach { file ->
                    file.insertImportXxxIfAbsent(newPackage)
                }
            }
        }

        //修复build.gradle文件的 namespace 语句
        private fun File.modifyBuildGradleFile(oldPackage: String, newPackage: String) {
            readText()
                .replace(
                    "namespace\\s+['\"]${oldPackage}['\"]".toRegex(),
                    "namespace '$newPackage'"
                )
                .replace(
                    "namespace\\s*=\\s*['\"]${oldPackage}['\"]".toRegex(),
                    """namespace = "$newPackage""""
                )
                .let { writeText(it) }
        }

        //修改AndroidManifest.xml文件，并返回新旧包名
        private fun File.modifyManifestFile(oldPackage: String, newPackage: String) {
            readText()
                .replaceWords("""package="$oldPackage"""", """package="$newPackage"""")
                .replaceWords("""android:name=".""", """android:name="$oldPackage.""")
                .let { writeText(it) }
        }

        private fun File.modifyLayoutXml(oldPackage: String) {
            readText()
                .replaceWords("""tools:context=".""", """tools:context="$oldPackage.""")
                .replaceWords("""app:layoutManager=".""", """app:layoutManager="$oldPackage.""")
                .let { writeText(it) }
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
    }
}