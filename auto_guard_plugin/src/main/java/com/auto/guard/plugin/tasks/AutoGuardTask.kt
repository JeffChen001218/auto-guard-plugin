package com.auto.guard.plugin.tasks

import com.auto.guard.plugin.extensions.AutoGuardExtension
import com.auto.guard.plugin.utils.generateMoveDirMap
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class AutoGuardTask @Inject constructor(
    private val pluginParams: AutoGuardExtension,
    private val variantName: String,
) : DefaultTask() {

    init {
        group = "guard"
    }

    @TaskAction
    fun execute() {
        val moveDirMap = project.generateMoveDirMap(pluginParams, variantName)

        // 1. execute FillImportTask
        FillImportTask.Proxy(
            this,
            project,
            pluginParams,
            variantName,
        ).execute()

        // 2. execute XmlClassGuardTask
        XmlClassGuardTask.Proxy(
            this,
            project,
            pluginParams,
            variantName,
            moveDirMap
        ).execute()

        // 3. execute MoveDirTask
        MoveDirTask.Proxy(
            this,
            project,
            pluginParams,
            variantName,
            moveDirMap
        ).execute()

        // 4. execute PackageChangeTask
        PackageChangeTask.Proxy(
            this,
            project,
            pluginParams,
            variantName
        ).execute()

        // 5. execute FixPackageDefineTask
        FixPackageDefineTask.Proxy(
            this,
            project,
            pluginParams,
            variantName
        ).execute()

        // 6. execute XmlBindingGuardTask
        RenameResTask.Proxy(
            this,
            project,
            pluginParams,
            variantName
        ).execute()

        // 7. execute ChangeResMd5Task
        ChangeResMd5Task.Proxy(
            this,
            project,
            pluginParams,
            variantName
        ).execute()
    }

}