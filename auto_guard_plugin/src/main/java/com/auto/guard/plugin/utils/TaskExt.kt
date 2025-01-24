package com.auto.guard.plugin.utils

import com.auto.guard.plugin.extensions.AutoGuardExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

//返回主module依赖的所有Android子module，包含间接依赖的
fun Task.allDependencyAndroidProjects(): List<Project> {
    val dependencyProjects = mutableListOf<Project>()
    project.findDependencyAndroidProject(dependencyProjects)
    val androidProjects = mutableListOf<Project>()
    androidProjects.add(project)
    androidProjects.addAll(dependencyProjects)
    return androidProjects
}


fun DefaultTask.getExcludeClassPathList(
    pluginParams: AutoGuardExtension,
    variantName: String
): List<String> {
    val sourceSets = project.sourceSetDirs(variantName)

    val excludeClassListInPackage = sourceSets.flatMap { sourceRoot ->
        sourceRoot.walkTopDown()
            .filter {
                it.isDirectory
            }
            // get all folder's package path
            .map { folderFile ->
                val relativePath = folderFile.relativeTo(sourceRoot).path
                val packagePath = relativePath.replace(File.separator, ".")
                Pair(folderFile, packagePath)
            }
            // find possible exclude package
            .filter { (folderFile, packagePath) ->
                pluginParams.excludes.forEach { exstr ->
                    val possibleFile = File(sourceRoot, exstr.replace(".", File.separator))
                    if (possibleFile.isDirectory) {
                        if (packagePath == exstr) {
                            return@filter true
                        }
                    }
                }
                return@filter false
            }
            .flatMap { (folderFile, packagePath) ->
                // find the sub files
                folderFile.walkTopDown()
                    .filter {
                        it.isFile
                    }
                    // map to clz path
                    .map { file ->
                        val relativePath = file.relativeTo(sourceRoot).path
                        val clzPath = relativePath.replace(File.separator, ".")
                            /* remove ext */
                            .getDirPath()
                        clzPath
                    }
            }
            .distinct()
            .toList()
    }
    val excludeClassList = sourceSets.flatMap { sourceRoot ->
        sourceRoot.walkTopDown()
            .filter {
                it.isFile
            }
            // get all file's clz path
            .map { file ->
                val relativePath = file.relativeTo(sourceRoot).path
                val clzPath = relativePath.replace(File.separator, ".")
                    /* remove ext */
                    .getDirPath()
                Pair(file, clzPath)
            }
            .filter { (file, clzPath) ->
                pluginParams.excludes.forEach { exStr ->
                    if (exStr.contains(".")) {
                        if (clzPath == exStr) {
                            return@filter true
                        }
                    } else {
                        if (exStr == file.nameWithoutExtension) {
                            return@filter true
                        }
                    }
                }
                return@filter false
            }
            .map { (file, clzPath) ->
                clzPath
            }
            .distinct()
            .toList()
    }
    return listOf(excludeClassListInPackage, excludeClassList).flatten()
}