package com.only.codegen

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

        project.tasks.register("genEntity", GenEntityTask::class.java) { task ->
            task.description = "Generate entity classes from database schema"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        project.tasks.register("genAnnotation", GenAnnotationTask::class.java) { task ->
            task.description = "Generate code from annotations (Repository, Service, etc.)"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }
//
//        project.tasks.register("genRepository", GenRepositoryTask::class.java) { task ->
//            task.group = "cap4k codegen"
//            task.description = "Generate repository classes"
//            task.extension.set(extension)
//        }
//
//        project.tasks.register("genDesign", GenDesignTask::class.java) { task ->
//            task.description = "Generate design elements (commands, queries, events)"
//            task.extension.set(extension)
//        }
    }
}
