package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.allDependencyAndroidProjects
import com.auto.guard.plugin.utils.findPackage
import com.auto.guard.plugin.utils.findXmlDirs
import com.auto.guard.plugin.utils.insertImportXxxIfAbsent
import com.auto.guard.plugin.utils.manifestFile
import com.auto.guard.plugin.utils.replaceWords
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

        fun execute() {
            val packageExtension = pluginParams.packageChange ?: emptyMap()
            if (packageExtension.isEmpty()) return
            val androidProjects = task.allDependencyAndroidProjects()
            androidProjects.forEach { it.changePackage(packageExtension) }
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
    }
}