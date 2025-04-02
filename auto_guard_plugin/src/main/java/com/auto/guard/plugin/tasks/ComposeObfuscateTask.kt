package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class ComposeObfuscateTask @Inject constructor(
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

        companion object {
            private val PUBLIC_MTD_NAME_REG =
                """@Composable\s+fun\s+(<(\w+\s*:\s*\w+\s*,?)*>\s+)?(\w+\.)?(\w+)\s*\(""".toRegex(RegexOption.MULTILINE)
            private val PRIVATE_MTD_NAME_REG =
                """@Composable\s+private\s+fun\s+(<(\w+\s*:\s*\w+\s*,?)*>\s+)?(\w+\.)?(\w+)\s*\(""".toRegex(RegexOption.MULTILINE)
        }

        fun execute() {
            val codeDirs = project.sourceSetDirs(variantName)
            codeDirs.forEach { sourceRoot ->
                val ktFiles = sourceRoot.walkTopDown()
                    .filter {
                        it.isFile && (it.extension == "kt")
                    }
                    .distinct()
                    .toList()

//                // 生成随机文件名
//                val usedFileNames = mutableSetOf<String>()
//                fun generateRandomFileName(): String {
//                    val upperLetters = ('A'..'Z').toList()
//                    val chars = ('a'..'z') + ('0'..'9') + '_'
//                    while (true) {
//                        val name = buildString {
//                            append(upperLetters.random()) // 确保以大写字母开头
//                            repeat(7) { append(chars.random()) } // 生成8位随机名称
//                        }
//                        if (name !in usedFileNames) {
//                            usedFileNames.add(name)
//                            return name
//                        }
//                    }
//                }
//
//                // 重命名kt文件
//                ktFiles.forEach { ktFile ->
//                    val newName = generateRandomFileName() + ".kt"
//                    val newFile = ktFile.parentFile.resolve(newName)
//                    ktFile.renameTo(newFile)
//                }

                // 收集所有@Composable函数信息
                val composableFunctionMap = mutableMapOf<String, String>() // 原函数名 -> 新函数名
                val functionLocationMap = mutableMapOf<String, MutableList<Pair<File, Int>>>() // 函数名 -> 文件位置列表

                val sameNameWithInnerMthKtFileRenameMap = mutableMapOf<File, String>()

                // 生成随机函数名
                val usedNames = mutableSetOf<String>()
                fun generateRandomName(): String {
                    val letters = ('A'..'Z').toList()
                    val chars = ('a'..'z') + ('0'..'9') + '_'
                    while (true) {
                        val name = buildString {
                            append(letters.random()) // 确保以大写字母开头
                            repeat((9..15).random()) { append(chars.random()) } // 生成9-15位随机名称
                        }
                        if (name !in usedNames) {
                            usedNames.add(name)
                            return name
                        }
                    }
                }

                ktFiles.forEach { ktFile ->
                    var fileContent = ktFile.readText()
                    val packageName = fileContent.lines()
                        .firstOrNull { it.startsWith("package ") }
                        ?.substringAfter("package ")
                        ?.trim()
                        ?: return@forEach

                    PUBLIC_MTD_NAME_REG.findAll(fileContent)
                        .forEach { match ->
                            val functionName = match.groupValues.lastOrNull()
                            if (functionName == ktFile.nameWithoutExtension
                                && !sameNameWithInnerMthKtFileRenameMap.contains(ktFile)
                            ) {
                                sameNameWithInnerMthKtFileRenameMap[ktFile] = ""
                            }
                            val fullName = "$packageName.$functionName"
                            composableFunctionMap[fullName] = "" // 先占位，后面会更新
                            functionLocationMap.getOrPut(fullName) { mutableListOf() }
                                .add(ktFile to match.range.first)
                        }

                    // 先把私有方法的名字换了
                    PRIVATE_MTD_NAME_REG.findAll(fileContent)
                        .forEach { match ->
                            val functionName = match.groupValues.lastOrNull()
                            val newRandomName = generateRandomName()
                            fileContent = fileContent.replace(
                                "(?<!\\.)\\b$functionName\\s*(\\()".toRegex(),
                                "$newRandomName("
                            )
                            fileContent = fileContent.replace(
                                "(?<!\\.)\\b$functionName\\s*(\\{)".toRegex(),
                                "$newRandomName{"
                            )
                        }
                    ktFile.writeText(fileContent)
                }

                // 更新函数名映射
                composableFunctionMap.keys.forEach { fullName ->
                    if (composableFunctionMap[fullName].isNullOrBlank()) {
                        val newRandomName = generateRandomName()
                        val functionName = fullName.substringAfterLast(".")
                        sameNameWithInnerMthKtFileRenameMap.toList()
                            .find { it.first.nameWithoutExtension == functionName }
                            ?.first?.let {
                                sameNameWithInnerMthKtFileRenameMap[it] = newRandomName
                            }
                        composableFunctionMap[fullName] = newRandomName
                    }
                }

                // 更新所有文件中的函数名和import语句
                ktFiles.forEach { ktFile ->
                    var fileContent = ktFile.readText()

                    // 更新import语句
                    composableFunctionMap.forEach { (oldFullName, newName) ->
                        val newFullName = oldFullName.split(".").dropLast(1).toMutableList().apply { add(newName) }
                            .joinToString(".")
                        fileContent = fileContent.replace(oldFullName, newFullName)
                    }

                    // 更新函数定义
                    composableFunctionMap.forEach { (fullName, newName) ->
                        val packageName = fullName.substringBeforeLast(".")
                        val oldName = fullName.substringAfterLast(".")
                        if (packageName == ktFile.readText().lines()
                                .firstOrNull { it.startsWith("package ") }
                                ?.substringAfter("package ")
                                ?.trim()
                        ) {
                            fileContent = fileContent.replace(
                                """(@Composable\s+)?(fun\s+(<(\w+\s*:\s*\w+\s*,?)*>\s+)?(\w+\.)?)${oldName}\s*\(""".toRegex(
                                    RegexOption.MULTILINE
                                ),
                                "$1$2$newName("
                            )
                        }
                    }

                    // 更新函数调用
                    composableFunctionMap.forEach { (fullName, newName) ->
                        val mtdPackageName = fullName.substringBeforeLast(".")
                        val filePackageName = fileContent.lines()
                            .firstOrNull { it.startsWith("package ") }
                            ?.substringAfter("package ")
                            ?.trim()


                        val oldName = fullName.substringAfterLast(".")
                        if (fileContent.contains(fullName.replace(oldName, newName))) {
                            fileContent = fileContent.replace(
                                "\\b$oldName\\s*(\\()".toRegex(),
                                "$newName("
                            )
                            fileContent = fileContent.replace(
                                "\\b$oldName\\s*(\\{)".toRegex(),
                                "$newName{"
                            )
                        }
                        if (mtdPackageName == filePackageName) {
                            fileContent = fileContent.replace(
                                "(?<!\\.)\\b$oldName\\s*(\\()".toRegex(),
                                "$newName("
                            )
                            fileContent = fileContent.replace(
                                "(?<!\\.)\\b$oldName\\s*(\\{)".toRegex(),
                                "$newName{"
                            )
                        }

                        // 更新 @旧名字 → @新名字
                        fileContent = fileContent.replace("@${oldName}", "@${newName}")
                    }


                    ktFile.writeText(fileContent)
                }

                sameNameWithInnerMthKtFileRenameMap.forEach { (file, newName) ->
                    if (file.exists()) file.renameTo(File(file.parent, "${newName}.kt"))
                }
            }
        }
    }
}