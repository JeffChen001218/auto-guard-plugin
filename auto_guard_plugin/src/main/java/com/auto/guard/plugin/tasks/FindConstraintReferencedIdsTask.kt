package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.model.aabResGuard
import com.auto.guard.plugin.model.andResGuard
import com.auto.guard.plugin.utils.findLayoutDirs
import com.auto.guard.plugin.utils.isAndroidProject
import com.bytedance.android.plugin.extensions.AabResGuardExtension
import com.tencent.gradle.AndResGuardExtension
import groovy.util.Node
import groovy.xml.XmlParser
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Collections
import javax.inject.Inject


open class FindConstraintReferencedIdsTask @Inject constructor(
    val extensionName: String,
    val pluginParams: AutoGuardExtension,
    val variantName: String,
) : DefaultTask() {

    init {
        group = "guard"
    }

    @TaskAction
    fun execute() {
        val layoutDirs = mutableListOf<File>()
        project.rootProject.subprojects {
            if (it.isAndroidProject()) {
                layoutDirs.addAll(it.findLayoutDirs(variantName))
            }
        }
        val set = findReferencedIds(layoutDirs)
        println("ids size is ${set.size} \n$set")
        val extension = project.extensions.getByName(extensionName)
        val whiteList =
            if (andResGuard == extensionName && extension is AndResGuardExtension) {
                extension.whiteList
            } else if (aabResGuard == extensionName && extension is AabResGuardExtension) {
                extension.whiteList
            } else {
                throw IllegalArgumentException("extensionName is $extensionName")
            }
        (whiteList as MutableCollection<String>).addAll(set)
    }

    private fun findReferencedIds(layoutDirs: List<File>): Collection<String> {
        val set = HashSet<String>()
        layoutDirs
            .flatMap {
                val listFiles: Array<File>? = it.listFiles { file -> file.name.endsWith(".xml") }
                listFiles?.toMutableList() ?: Collections.emptyList()
            }.forEach { layoutFile ->
                set.addAll(layoutFile.findReferencedIds())
            }
        return set
    }

    private fun File.findReferencedIds(): Set<String> {
        val set = HashSet<String>()
        val childrenList = XmlParser(false, false).parse(this).breadthFirst()
        for (children in childrenList) {
            val childNode = children as? Node ?: continue
            val ids = childNode.attribute("app:constraint_referenced_ids")?.toString()
            if (ids.isNullOrBlank()) continue
            ids.split(",").forEach {
                val id = it.trim()
                if (id.isNotEmpty()) {
                    set.add(if (aabResGuard == extensionName) "*.R.id.${id}" else "R.id.${id}")
                }
            }
        }
        return set
    }
}



