package com.only.codegen

import com.alibaba.fastjson.JSON
import com.only.codegen.misc.loadFileContent
import com.only.codegen.misc.resolveDirectory
import com.only.codegen.pebble.PebbleConfig
import com.only.codegen.pebble.PebbleInitializer
import com.only.codegen.template.PathNode
import com.only.codegen.template.Template
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class GenArchTask : AbstractCodegenTask() {

    @get:Input
    override val extension: Property<CodegenExtension> =
        project.objects.property(CodegenExtension::class.java)

    @TaskAction
    open fun generate() = genArch()

    private fun genArch() {
        val ext = extension.get()
        val config = PebbleConfig(
            encoding = ext.archTemplateEncoding.get()
        )
        PebbleInitializer.initPebble(config)

        val archTemplate = validateAndGetArchTemplate(ext) ?: return

        template = loadTemplate(archTemplate, ext)
        render(template!!, project.projectDir.absolutePath)

    }

    private fun loadTemplate(templatePath: String, ext: CodegenExtension): Template {
        val templateContent = loadFileContent(templatePath, ext.archTemplateEncoding.get())

        PathNode.setDirectory(resolveDirectory(templatePath, project.projectDir.absolutePath))

        return JSON.parseObject(templateContent, Template::class.java).apply {
            resolve(baseMap)
        }
    }

    private fun validateAndGetArchTemplate(ext: CodegenExtension): String? {
        val archTemplate = ext.archTemplate.orNull?.takeIf { it.isNotBlank() }
            ?: run {
                logger.error("请设置(archTemplate)参数")
                return null
            }

        if (ext.basePackage.get().isBlank()) {
            logger.warn("请设置(basePackage)参数")
            return null
        }

        return archTemplate
    }
}

