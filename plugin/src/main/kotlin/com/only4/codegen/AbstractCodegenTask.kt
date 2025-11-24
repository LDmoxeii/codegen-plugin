package com.only4.codegen

import com.only4.codegen.context.BaseContext
import com.only4.codegen.core.*
import com.only4.codegen.template.Template
import com.only4.codegen.template.TemplateNode
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 代码生成任务抽象基类
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
abstract class AbstractCodegenTask : DefaultTask(), BaseContext {

    init {
        group = "code gen"
    }

    companion object {
        const val FLAG_DO_NOT_OVERWRITE = "[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]"
        const val PATTERN_SPLITTER = "[,;]"
        const val PATTERN_LINE_BREAK = "\\r\\n|[\\r\\n]"
        const val DEFAULT_MUL_PRI_KEY_NAME = "Key"
    }

    @get:Input
    abstract val extension: Property<CodegenExtension>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectGroup: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val projectDir: Property<String>

    @Internal
    protected var template: Template? = null

    @Internal
    protected open val aliasRegistry = AliasRegistry()

    @get:Internal
    protected open val logAdapter: LoggerAdapter by lazy { GradleLoggerAdapter(logger) }

    @get:Internal
    protected open val fileWriter: FileWriter by lazy { DefaultFileWriter(logAdapter) }

    @get:Internal
    protected open val pathRenderer: PathRenderer by lazy {
        PathRenderer(
            fileWriter = fileWriter,
            basePackageProvider = { getString("basePackage") },
            outputEncodingProvider = { extension.get().outputEncoding.get() },
            templateProvider = { template ?: error("template must be initialized before rendering") },
            templatePackage = templatePackage,
            templateParentPath = templateParentPath,
            patternSplitter = Regex(PATTERN_SPLITTER),
            protectFlag = FLAG_DO_NOT_OVERWRITE,
        )
    }

    private val CodegenExtension.adapterPath: String
        get() = modulePath(moduleNameSuffix4Adapter.get())

    private val CodegenExtension.applicationPath: String
        get() = modulePath(moduleNameSuffix4Application.get())

    private val CodegenExtension.domainPath: String
        get() = modulePath(moduleNameSuffix4Domain.get())

    private fun CodegenExtension.modulePath(suffix: String): String =
        if (multiModule.get()) {
            "${projectDir.get()}${File.separator}${projectName.get()}$suffix"
        } else {
            projectDir.get()
        }

    @get:Internal
    override val baseMap: Map<String, Any?> by lazy {
        buildMap {
            val ext = extension.get()

            // 项目信息
            put("artifactId", projectName.get())
            put("groupId", projectGroup.get())
            put("version", projectVersion.get())

            // 基础配置
            put("archTemplate", ext.archTemplate.get())
            put("archTemplateEncoding", ext.archTemplateEncoding.get())
            put("outputEncoding", ext.outputEncoding.get())
            put("designFiles", ext.designFiles.files)
            put("basePackage", ext.basePackage.get())
            put("basePackage__as_path", ext.basePackage.get().replace(".", File.separator))
            put("multiModule", ext.multiModule.get())

            // 模块路径
            put("adapterModulePath", ext.adapterPath)
            put("applicationModulePath", ext.applicationPath)
            put("domainModulePath", ext.domainPath)

            // 数据库配置
            with(ext.database) {
                put("dbUrl", url.get())
                put("dbUsername", username.get())
                put("dbPassword", password.get())
                put("dbSchema", schema.get())
                put("dbTables", tables.get())
                put("dbIgnoreTables", ignoreTables.get())
            }

            // 生成配置
            with(ext.generation) {
                put("versionField", versionField.get())
                put("deletedField", deletedField.get())
                put("readonlyFields", readonlyFields.get())
                put("ignoreFields", ignoreFields.get())
                put("entityBaseClass", entityBaseClass.get())
                put("rootEntityBaseClass", rootEntityBaseClass.get())
                put("entityClassExtraImports", entityClassExtraImports.get())
                put("entitySchemaOutputPackage", entitySchemaOutputPackage.get())
                put("entitySchemaOutputMode", entitySchemaOutputMode.get())
                put("entitySchemaNameTemplate", entitySchemaNameTemplate.get())
                put("aggregateTypeTemplate", aggregateTypeTemplate.get())
                put("repositoryNameTemplate", repositoryNameTemplate.get())
                put("idGenerator", idGenerator.get())
                put("idGenerator4ValueObject", idGenerator4ValueObject.get())
                put("hashMethod4ValueObject", hashMethod4ValueObject.get())
                put("fetchType", fetchType.get())
                put("enumValueField", enumValueField.get())
                put("enumNameField", enumNameField.get())
                put("enumUnmatchedThrowException", enumUnmatchedThrowException.get())
                put("datePackage", datePackage.get())
                put("generateDbType", generateDbType.get())
                put("generateSchema", generateSchema.get())
                put("generateAggregate", generateAggregate.get())
                put("generateParent", generateParent.get())
                put("repositorySupportQuerydsl", repositorySupportQuerydsl.get())
            }

            // 其他配置
            put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
            put("SEPARATOR", File.separator)
            put("separator", File.separator)

        }.toMap()
    }

    @get:Internal
    override val adapterPath: String by lazy { extension.get().adapterPath }

    @get:Internal
    override val applicationPath: String by lazy { extension.get().applicationPath }

    @get:Internal
    override val domainPath: String by lazy { extension.get().domainPath }

    @get:Internal
    override val typeMapping: MutableMap<String, String> by lazy {
        extension.get().generation.typeMapping.get().toMutableMap()
    }

    @Internal
    override val templateParentPath: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val templatePackage: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()


    override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) =
        aliasRegistry.resolve(tag, variable).forEach { alias ->
            this[alias] = value
        }

    protected open fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) = pathRenderer.renderTemplate(templateNodes, parentPath)
}
