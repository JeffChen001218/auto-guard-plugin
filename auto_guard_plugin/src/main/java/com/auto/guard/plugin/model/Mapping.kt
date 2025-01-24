package com.auto.guard.plugin.model

import com.auto.guard.plugin.utils.KtFileParser
import com.auto.guard.plugin.utils.findLocationProject
import com.auto.guard.plugin.utils.findPackage
import com.auto.guard.plugin.utils.generateRandomName
import com.auto.guard.plugin.utils.generateRandomPackagePath
import com.auto.guard.plugin.utils.getDirPath
import com.auto.guard.plugin.utils.insertImportXxxIfAbsent
import com.auto.guard.plugin.utils.removeSuffix
import com.auto.guard.plugin.utils.sourceSetDirs
import org.gradle.api.Project
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.Writer

class Mapping constructor(
    val folderLevelRange: Pair<Int, Int>,
    val nameLengthRange: Pair<Int, Int>,
    val moveDirMap: Map<String, String>,
    val excludeClzPathList: List<String>
) {

    companion object {
        internal const val DIR_MAPPING = "dir mapping:"
        internal const val CLASS_MAPPING = "class mapping:"
    }

    internal val dirMapping = mutableMapOf<String, String>()
    internal val classMapping = mutableMapOf<String, String>()

    //类名索引
    internal var classIndex = -1L

    //包名索引
    internal var packageNameIndex = -1L

    //遍历文件夹下的所有直接子类，混淆文件名及移动目录
    fun obfuscateAllClass(project: Project, variantName: String): MutableMap<String, String> {
        val classMapped = mutableMapOf<String, String>()
        val iterator = dirMapping.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val rawDir = entry.key
            val locationProject = project.findLocationProject(rawDir, variantName)
            if (locationProject == null) {
                iterator.remove()
                continue
            }
            val manifestPackage = locationProject.findPackage()
            //过滤目录的直接子文件
            val dirPath = rawDir.replace(".", File.separator) //xx.xx  不带文件名
            val childFiles = locationProject.sourceSetDirs(variantName).flatMap {
                File(it, dirPath).listFiles { f ->
                    val filename = f.name
                    f.isFile && (filename.endsWith(".java") || filename.endsWith(".kt"))
                }?.toList() ?: emptyList()
            }
            if (childFiles.isEmpty()) continue
            for (file in childFiles) {
                val rawClassPath = "${rawDir}.${file.name.removeSuffix()}" //原始 xx.Xxx
                //已经混淆
                if (isObfuscated(rawClassPath)) continue
                if (rawDir == manifestPackage) {
                    file.insertImportXxxIfAbsent(manifestPackage)
                }
                if (rawClassPath in excludeClzPathList) continue
                val obfuscatePath = obfuscatePath(rawClassPath)  //混淆后 xx.Xxx
                val obfuscateRelativePath = obfuscatePath.replace(".", File.separator) //混淆后 xx/Xxx
                val rawRelativePath = rawClassPath.replace(".", File.separator) //原始 xx/Xxx
                //替换原始类路径
                val newFile =
                    File(file.absolutePath.replace(rawRelativePath, obfuscateRelativePath))
                if (!newFile.exists()) newFile.parentFile.mkdirs()
                if (file.renameTo(newFile)) {
                    classMapped[rawClassPath] = obfuscatePath

                    //处理顶级类、方法及变量
                    val obfuscateDir = obfuscatePath.getDirPath()
                    val filename = file.name.removeSuffix()
                    val ktParser = KtFileParser(newFile, filename)
                    val jvmName = ktParser.jvmName
                    if (jvmName != null && jvmName != filename) {
                        classMapped["$rawDir.$jvmName"] = "$obfuscateDir.$jvmName"
                    } else if (jvmName == null &&
                        (ktParser.topFunNames.isNotEmpty() || ktParser.topFieldNames.isNotEmpty())
                    ) {
                        classMapped["${rawClassPath}Kt"] = "${obfuscatePath}Kt"
                    }
                    ktParser.getTopClassOrFunOrFieldNames().forEach {
                        classMapped["$rawDir.$it"] = "$obfuscateDir.$it"
                    }
                }
            }
        }
        return classMapped
    }

    fun isObfuscated(rawClassPath: String) =
        classMapping.containsValue(rawClassPath)
//        classMapping.containsValue(rawClassPath) || rawClassPath in excludeClzPathList

    //混淆包名+类名，返回混淆后的包名+类名
    fun obfuscatePath(classPath: String): String {
        var innerClassName: String? = null //内部类类名
        val rawClassPath = if (isInnerClass(classPath)) {
            val arr = classPath.split("$")
            innerClassName = arr[1]
            arr[0]
        } else {
            classPath
        }
        var obfuscateClassPath = classMapping[rawClassPath]
        if (obfuscateClassPath == null) {
            val rawPackage = rawClassPath.getDirPath()
            val obfuscatedClassName = generateRandomName(
                nameLengthRange.first,
                nameLengthRange.second,
                firstLetterCaps = true,
            )
            val configuredObfuscatedPackage = moveDirMap[rawPackage]
            if (configuredObfuscatedPackage != null) {
                // find configured moveDir map
                dirMapping[rawPackage] = "${configuredObfuscatedPackage}"
                obfuscateClassPath = "${configuredObfuscatedPackage}.${obfuscatedClassName}"
                classMapping[rawClassPath] = obfuscateClassPath
            } else {
                // generate new package path map
                val obfuscatePackage = obfuscatePackage(rawPackage)
                obfuscateClassPath = "$obfuscatePackage.${obfuscatedClassName}"
                classMapping[rawClassPath] = obfuscateClassPath
            }
        }
        return if (innerClassName != null) "$obfuscateClassPath\$$innerClassName" else obfuscateClassPath
    }


    fun writeMappingToFile(mappingFile: File) {
        val writer: Writer = BufferedWriter(FileWriter(mappingFile, false))

        writer.write("$DIR_MAPPING\n")
        for ((key, value) in dirMapping) {
            writer.write(String.format("\t%s -> %s\n", key, value))
        }
        writer.write("\n")
        writer.flush()

        writer.write("$CLASS_MAPPING\n")
        for ((key, value) in classMapping.entries) {
            writer.write(String.format("\t%s -> %s\n", key, value))
        }
        writer.flush()

        writer.close()
    }

    //混淆包名，返回混淆后的包名
    private fun obfuscatePackage(rawPackage: String): String {
        var obfuscatePackage = dirMapping[rawPackage]
        if (obfuscatePackage == null) {
            obfuscatePackage = generateRandomPackagePath(
                folderLevelRange.first,
                folderLevelRange.second,
                nameLengthRange.first,
                nameLengthRange.second,
            )
            dirMapping[rawPackage] = obfuscatePackage
        }
        return obfuscatePackage
    }

    private fun isInnerClass(classPath: String): Boolean {
        return classPath.contains("[a-zA-Z0-9_]+\\$[a-zA-Z0-9_]+".toRegex())
    }
}