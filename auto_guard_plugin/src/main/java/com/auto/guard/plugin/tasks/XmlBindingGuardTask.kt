package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.findXmlDirs
import com.auto.guard.plugin.utils.generateRandomName
import com.auto.guard.plugin.utils.manifestFile
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class XmlBindingGuardTask @Inject constructor(
    val pluginParams: AutoGuardExtension,
    val variantName: String,
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
            variantName
        ).execute()
    }

    class Proxy(
        val task: DefaultTask,
        val project: Project,
        val pluginParams: AutoGuardExtension,
        val variantName: String,
    ) {

        fun execute() {    // 定义项目根目录（根据实际情况修改）
            val projectRoot = File("path/to/your/project")

            // 定义需要扫描的目录
            val resDirNames = listOf(
                "anim",
                "color",
                "drawable",
                "layout",
                "navigation",
                "raw",
                "values",
                "xml",
            )
            val codeFileTree =
                task.project.files(task.project.sourceSetDirs(variantName)).asFileTree

            // 遍历 所有 XML 文件
            resDirNames.forEach { resDirName ->
                val resDirs = task.project.findXmlDirs(variantName, resDirName)
                val resFiles = resDirs.flatMap {
                    it.listFiles { file -> file.isFile }?.toList() ?: emptyList()
                }.sortedByDescending { it.name.length }
                val allResFiles = resDirs.flatMap {
                    it.listFiles { file -> file.isFile }?.toList() ?: emptyList()
                }.sortedByDescending { it.name.length }
                val manifestFile = task.project.manifestFile()
                resFiles.forEach { curResFile ->
                    // 生成随机文件名
                    val oldName = curResFile.nameWithoutExtension
                    val ext = curResFile.extension
                    val newName = generateRandomName(resDirName)
                    // 重命名文件
                    val newFile = File(curResFile.parent, "${newName}.${ext}")
                    curResFile.renameTo(newFile)

                    // 1. 替换 @res/xxx
                    val oldAtRef = "@${resDirName.resType()}/$oldName"
                    val newAtRef = "@${resDirName.resType()}/$newName"
                    replaceInFile(manifestFile, oldAtRef, newAtRef)
                    replaceInFile(newFile, oldAtRef, newAtRef)
                    // 替换 资源文件 中的引用
                    allResFiles.forEach { resFile ->
                        replaceInFile(resFile, oldAtRef, newAtRef)
                    }
                    // 替换 代码文件 中的引用
                    codeFileTree.forEach { codeFile ->
                        replaceInFile(codeFile, oldAtRef, newAtRef)
                    }

                    // 2. 替换 R.res.xxx
                    val oldRRef = "R.${resDirName.resType()}.$oldName"
                    val newRRef = "R.${resDirName.resType()}.$newName"
                    replaceInFile(manifestFile, oldRRef, newRRef)
                    replaceInFile(newFile, oldRRef, newRRef)
                    // 替换 资源文件 中的引用
                    allResFiles.forEach { resFile ->
                        replaceInFile(resFile, oldRRef, newRRef)
                    }
                    // 替换 代码文件 中的引用
                    codeFileTree.forEach { codeFile ->
                        replaceInFile(codeFile, oldRRef, newRRef)
                    }

                    // 3. 替换 xxxBinding引用(仅对于layout资源)
                    if (resDirName.resType() == "layout") {
                        val oldBindingName = oldName.toCamelCase() + "Binding"
                        val newBindingName = newName.toCamelCase() + "Binding"
                        replaceInFile(manifestFile, oldBindingName, newBindingName)
                        replaceInFile(newFile, oldBindingName, newBindingName)
                        // 替换 资源文件 中的引用
                        allResFiles.forEach { resFile ->
                            replaceInFile(resFile, oldBindingName, newBindingName)
                        }
                        // 替换 代码文件 中的引用
                        codeFileTree.forEach { codeFile ->
                            replaceInFile(codeFile, oldBindingName, newBindingName)
                        }
                    }

                    println("rename xml: $oldName -> $newName")
                }
            }
        }

        fun String.toCamelCase(): String {
            return this.split('_') // 按 '_' 分割字符串
                .joinToString("") { part ->
                    part.replaceFirstChar { it.uppercase() } // 将每个部分的首字母大写
                }
        }

        fun String.resType(): String {
            return this.split('-')[0]
        }

        private val usedNames = mutableMapOf<String, MutableList<String>>()

        // 定义随机生成纯英文小写文件名的函数
        fun generateRandomName(resType: String): String {
            var result = ""
            if (usedNames[resType] == null) {
                usedNames[resType] = mutableListOf()
            }
            while (result.isBlank() || result in usedNames[resType]!!) {
                result = generateRandomName(5, 10)
            }
            usedNames[resType] = usedNames[resType]!!.apply {
                add(result)
            }
            return result
        }

        // 替换文件内容中的字符串
        fun replaceInFile(file: File, oldText: String, newText: String) {
            if (!file.exists()) {
                return
            }
            val content = file.readText()
            val newContent = content.replace(oldText, newText)
            file.writeText(newContent)
        }
    }

}