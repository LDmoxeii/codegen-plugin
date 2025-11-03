package com.only4.codegen

import com.google.gson.Gson
import com.only4.codegen.misc.loadFileContent
import com.only4.codegen.misc.resolveDirectory
import com.only4.codegen.pebble.PebbleConfig
import com.only4.codegen.pebble.PebbleInitializer
import com.only4.codegen.template.PathNode
import com.only4.codegen.template.Template
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputDirectory
import java.io.File
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import org.gradle.api.tasks.CacheableTask

@CacheableTask
open class GenArchTask : AbstractCodegenTask() {

    @get:Input
    override val extension: Property<CodegenExtension> =
        project.objects.property(CodegenExtension::class.java)

    @get:Input
    override val projectName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectGroup: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectDir: Property<String> = project.objects.property(String::class.java)

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val archTemplateFile
        get() = project.file(extension.get().archTemplate.get())

    // Outputs (module roots) – actual files are under src/main/... based on templates
    @get:OutputDirectory
    val outputAdapterModuleDir: File
        get() = File(adapterPath)

    @get:OutputDirectory
    val outputApplicationModuleDir: File
        get() = File(applicationPath)

    @get:OutputDirectory
    val outputDomainModuleDir: File
        get() = File(domainPath)

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
        render(template!!, projectDir.get())

    }

    private fun loadTemplate(templatePath: String, ext: CodegenExtension): Template {
        val templateContent = loadFileContent(templatePath, ext.archTemplateEncoding.get())

        val baseDir = resolveDirectory(templatePath, projectDir.get())
        templateBaseDir = baseDir

        val ctxWithDir = buildMap<String, Any?> {
            putAll(baseMap)
            put("templateBaseDir", baseDir)
        }

        return Gson().fromJson(templateContent, Template::class.java).apply {
            resolve(ctxWithDir)
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

