package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.generateRandomName
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class InsertObfuscatedCodeTask @Inject constructor(
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
                file.insertIfCondition()
            }
        }

        private fun File.insertIfCondition() {
            val content = readText(Charsets.UTF_8)

            var newContent = StringBuilder(content)

            Regex("""if\s*\(""")
                .findAll(content)
                .map {
                    it.range.last + 1
                }
                // 从后向前插入，保证未被插入的索引位置不发生变化
                .toList().reversed()
                .forEach { ifConditionStartIndex ->
                    newContent.insert(
                        ifConditionStartIndex,
                        "${generateRandomTrueCondition(extension == "java" || extension == "aidl")}&&"
                    )
                }

            writeText(newContent.toString())
        }

        private fun generateRandomTrueCondition(isJava: Boolean): String {
            val trueCondition = when ((1..3).random()) {
                1 -> {
                    val randomStr = generateRandomName(1, 5)
                    "\"${randomStr}\".split(\"\").${if (isJava) "length" else "size"}==${randomStr.length}"
                }

                2 -> {
                    val randomStr1 = generateRandomName(5, 10)
                    val randomStr2 = generateRandomName(5, 10)
                    val lengthField = if (isJava) "length" else "size"
                    val compare = if (randomStr1.length == randomStr2.length) "==" else "!="
                    "\"${randomStr1}\".split(\"\").${lengthField}${compare}\"${randomStr2}\".split(\"\").${lengthField}"
                }

                3 -> {
                    val randomStr = generateRandomName(10, 15)
                    val strArrDefineStr = randomStr.split("").map { "\"${it}\"" }.joinToString(",")
                    if (isJava) {
                        "String.join(\"\", new String[]{${strArrDefineStr}}).equals(\"${randomStr}\")"
                    } else {
                        "(java.lang.String.join(\"\", *arrayOf(${strArrDefineStr})) == \"${randomStr}\")"
                    }
                }

                else -> "true"
            }
            return "(${trueCondition})"
        }
    }

}