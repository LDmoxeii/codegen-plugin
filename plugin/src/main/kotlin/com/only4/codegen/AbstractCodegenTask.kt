package com.only4.codegen

import com.only4.codegen.context.BaseContext
import com.only4.codegen.misc.resolvePackage
import com.only4.codegen.template.PathNode
import com.only4.codegen.template.Template
import com.only4.codegen.template.TemplateNode
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.File
import java.nio.charset.Charset
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
        group = "cap4k codegen"
    }

    companion object {
        const val FLAG_DO_NOT_OVERWRITE = "[cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]"
        const val PATTERN_SPLITTER = "[,;]"
        const val PATTERN_DESIGN_PARAMS_SPLITTER = "[\\:]"
        const val PATTERN_LINE_BREAK = "\\r\\n|[\\r\\n]"
        const val AGGREGATE_PACKAGE = "domain.aggregates"
        const val DOMAIN_EVENT_SUBSCRIBER_PACKAGE = "application.subscribers.domain"
        const val INTEGRATION_EVENT_SUBSCRIBER_PACKAGE = "application.subscribers.integration"
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
    protected var renderFileSwitch = true

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

    /**
     * 全局 segment 上下文缓存
     * Key: "parentTag:tableName:segmentVar"
     * Value: segment context map
     */
    private val segmentContextCache = mutableMapOf<String, Map<String, Any?>>()

    protected fun getSegmentContext(key: String): Map<String, Any?>? {
        return segmentContextCache[key]
    }

    protected fun putSegmentContext(key: String, context: Map<String, Any?>) {
        segmentContextCache[key] = context
    }

    protected fun clearSegmentCache() {
        segmentContextCache.clear()
    }

    protected fun mergeSegmentContexts(
        parentTag: String,
        tableName: String,
        baseContext: MutableMap<String, Any?>
    ) {
        segmentContextCache.entries
            .filter { it.key.startsWith("$parentTag:$tableName:") }
            .forEach { (cacheKey, segmentContext) ->
                // Merge each segment context item
                segmentContext.forEach { (key, value) ->
                    baseContext.putContext(parentTag, key, value)
                }
            }
    }


    /**
     * 根据 tag 和 variable 生成别名列表
     * 使用正则匹配模式，支持多种命名约定
     */
    private fun alias4Template(tag: String, variable: String): List<String> {
        val key = "$tag.$variable"

        return when {
            // Module 相关 - 匹配所有 tag
            key.matches(Regex(".+\\.modulePath")) -> listOf(
                "modulePath",
                "ModulePath",
                "MODULE_PATH",
                "module_path",
                "Module_Path",
                "module",
                "Module",
                "MODULE"
            )

            // TemplatePackage 相关
            key.matches(Regex(".+\\.templatePackage")) ->
                listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package")

            // Imports 相关 - 匹配所有 tag
            key.matches(Regex(".+\\.Imports")) ->
                listOf(
                    "Imports",
                    "imports",
                    "IMPORTS",
                    "importList",
                    "ImportList",
                    "IMPORT_LIST",
                    "import_list",
                    "Import_List"
                )

            // Comment 相关 - 匹配所有 tag
            key.matches(Regex(".+\\.Comment")) -> listOf("Comment", "comment", "COMMENT")

            // CommentEscaped 相关 - 匹配所有 tag
            key.matches(Regex(".+\\.CommentEscaped")) -> listOf(
                "CommentEscaped",
                "commentEscaped",
                "COMMENT_ESCAPED",
                "Comment_Escaped"
            )

            // Aggregate 相关
            key.matches(Regex("(schema|enum|domain_event|domain_event_handler|specification|factory)\\.Aggregate")) ->
                listOf("Aggregate", "aggregate", "AGGREGATE")

            // Entity 相关
            key.matches(Regex("(schema|enum|domain_event|domain_event_handler|specification|factory)\\.Entity")) ->
                listOf(
                    "Entity",
                    "entity",
                    "ENTITY",
                    "entityType",
                    "EntityType",
                    "ENTITY_TYPE",
                    "Entity_Type",
                    "entity_type"
                )

            // EntityVar 相关
            key.matches(Regex("(schema|enum|domain_event|domain_event_handler|specification|factory)\\.EntityVar")) ->
                listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var")

            // SchemaBase 相关
            key.matches(Regex("(schema_base|schema)\\.SchemaBase")) ->
                listOf("SchemaBase", "schema_base", "SCHEMA_BASE")

            // Schema 特定字段
            key == "schema.IdField" -> listOf("IdField", "idField", "ID_FIELD", "id_field", "Id_Field")
            key == "schema.FIELD_ITEMS" -> listOf("FIELD_ITEMS", "fieldItems", "field_items", "Field_Items")
            key == "schema.JOIN_ITEMS" -> listOf("JOIN_ITEMS", "joinItems", "join_items", "Join_Items")

            // Schema Field 相关
            key == "schema_field.fieldType" -> listOf("fieldType", "FIELD_TYPE", "field_type", "Field_Type")
            key == "schema_field.fieldName" -> listOf("fieldName", "FIELD_NAME", "field_name", "Field_Name")
            key == "schema_field.fieldComment" -> listOf(
                "fieldComment",
                "FIELD_COMMENT",
                "field_comment",
                "Field_Comment"
            )

            // Enum 相关
            key == "enum.Enum" -> listOf(
                "Enum",
                "enum",
                "ENUM",
                "EnumType",
                "enumType",
                "ENUM_TYPE",
                "enum_type",
                "Enum_Type"
            )

            key == "enum.EnumValueField" -> listOf(
                "EnumValueField",
                "enumValueField",
                "ENUM_VALUE_FIELD",
                "enum_value_field",
                "Enum_Value_Field"
            )

            key == "enum.EnumNameField" -> listOf(
                "EnumNameField",
                "enumNameField",
                "ENUM_NAME_FIELD",
                "enum_name_field",
                "Enum_Name_Field"
            )

            key == "enum.EnumItems" -> listOf("EnumItems", "ENUM_ITEMS", "enumItems", "enum_items", "Enum_Items")

            // Domain Event 相关
            key.matches(Regex("(domain_event|domain_event_handler)\\.DomainEvent")) ->
                listOf(
                    "DomainEvent",
                    "domainEvent",
                    "DOMAIN_EVENT",
                    "domain_event",
                    "Domain_Event",
                    "Event",
                    "EVENT",
                    "event",
                    "DE",
                    "D_E",
                    "de",
                    "d_e"
                )

            key.matches(Regex("(domain_event|domain_event_handler)\\.persist")) ->
                listOf("persist", "Persist", "PERSIST")

            // Domain Service 相关
            key == "domain_service.DomainService" ->
                listOf(
                    "DomainService",
                    "domainService",
                    "DOMAIN_SERVICE",
                    "domain_service",
                    "Domain_Service",
                    "Service",
                    "SERVICE",
                    "service",
                    "Svc",
                    "SVC",
                    "svc",
                    "DS",
                    "D_S",
                    "ds",
                    "d_s"
                )

            // Specification 相关
            key == "specification.Specification" ->
                listOf("Specification", "specification", "SPECIFICATION", "Spec", "SPEC", "spec")

            // Factory 相关
            key == "factory.Factory" ->
                listOf("Factory", "factory", "FACTORY", "Fac", "FAC", "fac")

            // Integration Event 相关
            key.matches(Regex("(integration_event|integration_event_handler)\\.IntegrationEvent")) ->
                listOf(
                    "IntegrationEvent",
                    "integrationEvent",
                    "integration_event",
                    "INTEGRATION_EVENT",
                    "Integration_Event",
                    "Event",
                    "EVENT",
                    "event",
                    "IE",
                    "I_E",
                    "ie",
                    "i_e"
                )

            // Aggregate Root 相关
            key.matches(Regex("(specification|factory|domain_event|domain_event_handler)\\.AggregateRoot")) ->
                listOf(
                    "AggregateRoot",
                    "aggregateRoot",
                    "aggregate_root",
                    "AGGREGATE_ROOT",
                    "Aggregate_Root",
                    "Root",
                    "ROOT",
                    "root",
                    "AR",
                    "A_R",
                    "ar",
                    "a_r"
                )

            // Client 相关
            key.matches(Regex("(client|client_handler)\\.Client")) ->
                listOf("Client", "client", "CLIENT", "Cli", "CLI", "cli")

            // Query 相关
            key.matches(Regex("(query|query_handler)\\.Query")) ->
                listOf("Query", "query", "QUERY", "Qry", "QRY", "qry")

            // Command 相关
            key.matches(Regex("(command|command_handler)\\.Command")) ->
                listOf("Command", "command", "COMMAND", "Cmd", "CMD", "cmd")

            // Request 相关
            key.matches(Regex("(client|client_handler|query|query_handler|command|command_handler)\\.Request")) ->
                listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param")

            // Response 相关
            key.matches(Regex("(client|client_handler|query|query_handler|command|command_handler|saga|saga_handler)\\.Response")) ->
                listOf(
                    "Response",
                    "response",
                    "RESPONSE",
                    "Res",
                    "RES",
                    "res",
                    "ReturnType",
                    "returnType",
                    "RETURN_TYPE",
                    "return_type",
                    "Return_Type",
                    "Return",
                    "RETURN",
                    "return"
                )

            // 默认返回原变量名
            else -> listOf(variable)
        }
    }

    override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) =
        alias4Template(tag, variable).forEach { alias ->
            this[alias] = value
        }

    protected fun forceRender(pathNode: PathNode, parentPath: String): String =
        renderFileSwitch.let { originalValue ->
            renderFileSwitch = true
            try {
                render(pathNode, parentPath)
            } finally {
                renderFileSwitch = originalValue
            }
        }

    protected fun render(pathNode: PathNode, parentPath: String): String =
        when (pathNode.type?.lowercase()) {
            "root" -> {
                pathNode.children?.forEach { render(it, parentPath) }
                parentPath
            }

            "dir" -> {
                val dirPath = renderDir(pathNode, parentPath)
                pathNode.children?.forEach { render(it, dirPath) }
                dirPath
            }

            "file" -> renderFile(pathNode, parentPath)
            else -> parentPath
        }

    protected open fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        templateNodes.forEach { templateNode ->
            templatePackage[templateNode.tag!!] = resolvePackage("${parentPath}${File.separator}X.kt")
                .substring(getString("basePackage").length + 1)
            templateParentPath[templateNode.tag!!] = parentPath
        }
    }

    private fun renderDir(pathNode: PathNode, parentPath: String): String {
        require(pathNode.type.equals("dir", ignoreCase = true)) { "pathNode must be a directory type" }

        val name = pathNode.name?.takeIf { it.isNotBlank() } ?: return parentPath
        val path = "$parentPath${File.separator}$name"
        val dirFile = File(path)

        when {
            !dirFile.exists() -> {
                dirFile.mkdirs()
                logger.info("创建目录: $path")
            }

            else -> when (pathNode.conflict.lowercase()) {
                "skip" -> logger.info("目录已存在，跳过: $path")
                "warn" -> logger.warn("目录已存在，继续: $path")
                "overwrite" -> {
                    logger.info("目录覆盖: $path")
                    dirFile.deleteRecursively()
                    dirFile.mkdirs()
                }
            }
        }

        pathNode.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            tag.split(Regex(PATTERN_SPLITTER))
                .filter { it.isNotBlank() }
                .forEach { renderTemplate(template!!.select(it), path) }
        }

        return path
    }

    protected fun renderFile(pathNode: PathNode, parentPath: String): String {
        require(pathNode.type.equals("file", ignoreCase = true)) { "pathNode must be a file type" }

        val name = pathNode.name?.takeIf { it.isNotBlank() }
            ?: error("pathNode name must not be blank")

        val path = "$parentPath${File.separator}$name"
        if (!renderFileSwitch) return path

        val file = File(path)
        val content = pathNode.data.orEmpty()
        val encoding = pathNode.encoding ?: extension.get().outputEncoding.get()
        val charset = Charset.forName(encoding)

        when {
            !file.exists() -> {
                file.parentFile?.mkdirs()
                file.writeText(content, charset)
                logger.info("创建文件: $path")
            }

            else -> when (pathNode.conflict.lowercase()) {
                "skip" -> logger.info("文件已存在，跳过: $path")
                "warn" -> logger.warn("文件已存在，继续: $path")
                "overwrite" -> {
                    if (file.readText(charset).contains(FLAG_DO_NOT_OVERWRITE)) {
                        logger.warn("文件已存在且包含保护标记，跳过: $path")
                    } else {
                        logger.info("文件覆盖: $path")
                        file.writeText(content, charset)
                    }
                }
            }
        }
        return path
    }

}
