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

        private val excludeClzPathList = task.getExcludeClassPathList(pluginParams, variantName)
        private val excludeClzFolderPathList = excludeClzPathList.map { it.getDirPath() }.distinct()

        fun execute() {
            if (moveDirMap.isEmpty()) return
            val androidProjects = task.allDependencyAndroidProjects()
            val validMoveDirMap = moveDirMap.filter {
                it.key !in excludeClzFolderPathList
            }
            androidProjects.forEach { it.moveDir(validMoveDirMap) }
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