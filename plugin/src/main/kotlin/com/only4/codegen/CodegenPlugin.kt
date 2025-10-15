package com.only4.codegen

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 代码生成 Gradle 插件
 */
class CodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("codegen", CodegenExtension::class.java)

        project.tasks.register("genArch", GenArchTask::class.java) { task ->
            task.description = "Generate project architecture structure"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        project.tasks.register("genAggregate", GenAggregateTask::class.java) { task ->
            task.description = "Generate Aggregate from database schema"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }


        project.tasks.register("genDesign", GenDesignTask::class.java) { task ->
            task.description = "Generate design elements (commands, queries, events)"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)

            // GenDesignTask depends on kspKotlin to read KSP metadata
            project.tasks.findByName("kspKotlin")?.let { kspTask ->
                task.dependsOn(kspTask)
            }
        }
    }
}
