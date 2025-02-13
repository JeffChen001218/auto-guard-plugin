package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.resDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.random.Random

open class ChangeResMd5Task @Inject constructor(
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

        fun execute() {
            val allResFiles = task.project.resDirs(variantName).flatMap {
                it.walkTopDown().filter { file -> file.isFile }.toList()
            }.sortedByDescending { it.name.length }

            allResFiles.forEach { resFile ->
                resFile.modifyMD5()
            }
        }

        // 计算文件 MD5 值的扩展函数
        fun File.calculateMD5(): String {
            val md = MessageDigest.getInstance("MD5")
            this.inputStream().use { fis ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        // 修改文件 MD5 值的扩展函数
        fun File.modifyMD5() {
            val originalMD5 = this.calculateMD5()
            val usedCombinations = mutableSetOf<String>()
            val whitespaceChars = listOf(' ', '\n', '\t')  // 空格、换行、制表符

            var modifiedMD5: String = originalMD5

            do {
                // 生成随机长度的空白字符组合 (1 到 200 个字符)
                val randomLength = Random.nextInt(1, 200)
                val randomCombination = (1..randomLength)
                    .map {
                        when {
                            extension in listOf(
                                "png", "webp",
                                "jpg", "jpeg",
                                "gif",
                                // "bmp",
                                // "tif", "tiff",
                                // "heif", "heic",
                            ) -> {
                                '\u0000'
                            }

                            else -> whitespaceChars.random()
                        }
                    }
                    .joinToString("")

                // 确保组合未被使用过
                if (randomCombination in usedCombinations) continue
                usedCombinations.add(randomCombination)

                // 将随机组合追加到文件末尾
                FileOutputStream(this, true).use { fos ->
                    fos.write(randomCombination.toByteArray())
                }

                // 计算新的 MD5
                modifiedMD5 = this.calculateMD5()

            } while (modifiedMD5 == originalMD5)  // 如果 MD5 没变，继续生成新的组合
            println("change ${name} md5:${originalMD5} -> ${modifiedMD5}")
        }

    }

}