package com.auto.guard.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import com.auto.guard.plugin.entensions.AutoGuardExtension
import com.auto.guard.plugin.model.aabResGuard
import com.auto.guard.plugin.model.andResGuard
import com.auto.guard.plugin.tasks.FindConstraintReferencedIdsTask
import com.auto.guard.plugin.tasks.MoveDirTask
import com.auto.guard.plugin.tasks.PackageChangeTask
import com.auto.guard.plugin.tasks.XmlClassGuardTask
import com.auto.guard.plugin.utils.AgpVersion
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import kotlin.reflect.KClass

class AutoGuardPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        checkApplicationPlugin(project)
        println("AutoGuard version is ${project.findProperty("plugin_version")}, agpVersion=${AgpVersion.agpVersion}")
        val guardExt = project.extensions.create("xmlClassGuard", AutoGuardExtension::class.java)

        val android = project.extensions.getByName("android") as AppExtension
        project.afterEvaluate {
            android.applicationVariants.all { variant ->
                it.createTasks(guardExt, variant)
            }
        }

//        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
//        androidComponents.onVariants {  variant ->
//            variant.instrumentation.transformClassesWith(
//                StringFogTransform::class.java,
//                InstrumentationScope.ALL) {
//            }
//            variant.instrumentation.setAsmFramesComputationMode(
//                FramesComputationMode.COPY_FRAMES
//            )
//        }
    }

    private fun Project.createTasks(guardExt: AutoGuardExtension, variant: ApplicationVariant) {
        val variantName = variant.name.capitalize()
        createTask("xmlClassGuard$variantName", XmlClassGuardTask::class, guardExt, variantName)
        createTask("packageChange$variantName", PackageChangeTask::class, guardExt, variantName)
        createTask("moveDir$variantName", MoveDirTask::class, guardExt, variantName)
        if (guardExt.findAndConstraintReferencedIds) {
            createAndFindConstraintReferencedIdsTask(variantName)
        }
        if (guardExt.findAabConstraintReferencedIds) {
            createAabFindConstraintReferencedIdsTask(variantName)
        }
    }

    private fun Project.createAndFindConstraintReferencedIdsTask(variantName: String) {
        val andResGuardTaskName = "resguard$variantName"
        val andResGuardTask = project.tasks.findByName(andResGuardTaskName)
            ?: throw GradleException("AndResGuard plugin required")
        val taskName = "andFindConstraintReferencedIds$variantName"
        val task =
            createTask(taskName, FindConstraintReferencedIdsTask::class, andResGuard, variantName)
        andResGuardTask.dependsOn(task)
    }

    private fun Project.createAabFindConstraintReferencedIdsTask(variantName: String) {
        val aabResGuardTaskName = "aabresguard$variantName"
        val aabResGuardTask = project.tasks.findByName(aabResGuardTaskName)
            ?: throw GradleException("AabResGuard plugin required")
        val taskName = "aabFindConstraintReferencedIds$variantName"
        val task =
            createTask(taskName, FindConstraintReferencedIdsTask::class, aabResGuard, variantName)
        aabResGuardTask.dependsOn(task)
    }

    private fun checkApplicationPlugin(project: Project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("Android Application plugin required")
        }
    }

    private fun <T : Task> Project.createTask(
        taskName: String,
        taskClass: KClass<T>,
        vararg params: Any
    ): Task = tasks.findByName(taskName) ?: tasks.create(taskName, taskClass.java, *params)
}