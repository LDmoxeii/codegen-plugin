package com.only.codegen

import com.only.codegen.context.BaseContext
import com.only.codegen.template.PathNode
import com.only.codegen.template.Template
import com.only.codegen.template.TemplateNode
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
        const val AGGREGATE_REPOSITORY_PACKAGE = "adapter.domain.repositories"
        const val AGGREGATE_PACKAGE = "domain.aggregates"
        const val DOMAIN_EVENT_SUBSCRIBER_PACKAGE = "application.subscribers.domain"
        const val INTEGRATION_EVENT_SUBSCRIBER_PACKAGE = "application.subscribers.integration"
        const val DEFAULT_MUL_PRI_KEY_NAME = "Key"
    }

    @get:Input
    abstract val extension: Property<CodegenExtension>

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
            "${project.projectDir.absolutePath}${File.separator}${project.name}$suffix"
        } else {
            project.projectDir.absolutePath
        }

    @get:Internal
    override val baseMap: Map<String, Any?> by lazy {
        buildMap {
            val ext = extension.get()

            // 项目信息
            put("artifactId", project.name)
            put("groupId", project.group.toString())
            put("version", project.version.toString())

            // 基础配置
            put("archTemplate", ext.archTemplate.get())
            put("archTemplateEncoding", ext.archTemplateEncoding.get())
            put("outputEncoding", ext.outputEncoding.get())
            put("basePackage", ext.basePackage.get())
            put("basePackage__as_path", ext.basePackage.get().replace(".", File.separator))
            put("multiModule", ext.multiModule.get().toString())

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
                put("repositoryNameTemplate", repositoryNameTemplate.get())
                put("repositorySupportQuerydsl", repositorySupportQuerydsl.get())
            }

            // 其他配置
            put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
            put("SEPARATOR", File.separator)
            put("separator", File.separator)

        }
    }

    @get:Internal
    override val adapterPath: String by lazy { extension.get().adapterPath }

    @get:Internal
    override val applicationPath: String by lazy { extension.get().applicationPath }

    @get:Internal
    override val domainPath: String by lazy { extension.get().domainPath }

    @get:Internal
    override val typeRemapping: MutableMap<String, String> by lazy {
        extension.get().generation.typeMapping.get().toMutableMap()
    }

    @Internal
    override val templateNodeMap = mutableMapOf<String, MutableList<TemplateNode>>()

    @Internal
    override val templateAliasMap = mapOf(
        // Comment 相关
        "schema.Comment" to listOf("Comment", "comment", "COMMENT"),
        "enum.Comment" to listOf("Comment", "comment", "COMMENT"),
        "domain_event.Comment" to listOf("Comment", "comment", "COMMENT"),
        "domain_event_handler.Comment" to listOf("Comment", "comment", "COMMENT"),
        "specification.Comment" to listOf("Comment", "comment", "COMMENT"),
        "factory.Comment" to listOf("Comment", "comment", "COMMENT"),
        "domain_service.Comment" to listOf("Comment", "comment", "COMMENT"),
        "integration_event.Comment" to listOf("Comment", "comment", "COMMENT"),
        "integration_event_handler.Comment" to listOf("Comment", "comment", "COMMENT"),
        "client.Comment" to listOf("Comment", "comment", "COMMENT"),
        "query.Comment" to listOf("Comment", "comment", "COMMENT"),
        "command.Comment" to listOf("Comment", "comment", "COMMENT"),
        "client_handler.Comment" to listOf("Comment", "comment", "COMMENT"),
        "query_handler.Comment" to listOf("Comment", "comment", "COMMENT"),
        "command_handler.Comment" to listOf("Comment", "comment", "COMMENT"),
        "saga.Comment" to listOf("Comment", "comment", "COMMENT"),

        // CommentEscaped 相关
        "schema.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "enum.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "domain_event.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "domain_event_handler.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "specification.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "factory.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "domain_service.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "integration_event.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "integration_event_handler.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "client.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "query.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "command.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "client_handler.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "query_handler.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "command_handler.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),
        "saga.CommentEscaped" to listOf("CommentEscaped", "commentEscaped", "COMMENT_ESCAPED", "Comment_Escaped"),

        // Aggregate 相关
        "schema.Aggregate" to listOf("Aggregate", "aggregate", "AGGREGATE"),
        "enum.Aggregate" to listOf("Aggregate", "aggregate", "AGGREGATE"),
        "domain_event.Aggregate" to listOf("Aggregate", "aggregate", "AGGREGATE"),
        "domain_event_handler.Aggregate" to listOf("Aggregate", "aggregate", "AGGREGATE"),
        "specification.Aggregate" to listOf("Aggregate", "aggregate", "AGGREGATE"),
        "factory.Aggregate" to listOf("Aggregate", "aggregate", "AGGREGATE"),

        // EntityPackage 相关
        "schema.entityPackage" to listOf("entityPackage", "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package"),
        "enum.entityPackage" to listOf("entityPackage", "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package"),
        "domain_event.entityPackage" to listOf("entityPackage", "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package"),
        "domain_event_handler.entityPackage" to listOf("entityPackage", "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package"),
        "specification.entityPackage" to listOf("entityPackage", "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package"),
        "factory.entityPackage" to listOf("entityPackage", "EntityPackage", "ENTITY_PACKAGE", "entity_package", "Entity_Package"),

        // TemplatePackage 相关
        "schema.templatePackage" to listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package"),
        "enum.templatePackage" to listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package"),
        "domain_event.templatePackage" to listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package"),
        "domain_event_handler.templatePackage" to listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package"),
        "specification.templatePackage" to listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package"),
        "factory.templatePackage" to listOf("templatePackage", "TemplatePackage", "TEMPLATE_PACKAGE", "template_package", "Template_Package"),

        // Entity 相关
        "schema.Entity" to listOf("Entity", "entity", "ENTITY", "entityType", "EntityType", "ENTITY_TYPE", "Entity_Type", "entity_type"),
        "enum.Entity" to listOf("Entity", "entity", "ENTITY", "entityType", "EntityType", "ENTITY_TYPE", "Entity_Type", "entity_type"),
        "domain_event.Entity" to listOf("Entity", "entity", "ENTITY", "entityType", "EntityType", "ENTITY_TYPE", "Entity_Type", "entity_type"),
        "domain_event_handler.Entity" to listOf("Entity", "entity", "ENTITY", "entityType", "EntityType", "ENTITY_TYPE", "Entity_Type", "entity_type"),
        "specification.Entity" to listOf("Entity", "entity", "ENTITY", "entityType", "EntityType", "ENTITY_TYPE", "Entity_Type", "entity_type"),
        "factory.Entity" to listOf("Entity", "entity", "ENTITY", "entityType", "EntityType", "ENTITY_TYPE", "Entity_Type", "entity_type"),

        // EntityVar 相关
        "schema.EntityVar" to listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),
        "enum.EntityVar" to listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),
        "domain_event.EntityVar" to listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),
        "domain_event_handler.EntityVar" to listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),
        "specification.EntityVar" to listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),
        "factory.EntityVar" to listOf("EntityVar", "entityVar", "ENTITY_VAR", "entity_var", "Entity_Var"),

        // Schema 相关
        "schema_base.SchemaBase" to listOf("SchemaBase", "schema_base", "SCHEMA_BASE"),
        "schema.SchemaBase" to listOf("SchemaBase", "schema_base", "SCHEMA_BASE"),
        "schema.IdField" to listOf("IdField", "idField", "ID_FIELD", "id_field", "Id_Field"),
        "schema.FIELD_ITEMS" to listOf("FIELD_ITEMS", "fieldItems", "field_items", "Field_Items"),
        "schema.JOIN_ITEMS" to listOf("JOIN_ITEMS", "joinItems", "join_items", "Join_Items"),

        // Schema Field 相关
        "schema_field.fieldType" to listOf("fieldType", "FIELD_TYPE", "field_type", "Field_Type"),
        "schema_field.fieldName" to listOf("fieldName", "FIELD_NAME", "field_name", "Field_Name"),
        "schema_field.fieldComment" to listOf("fieldComment", "FIELD_COMMENT", "field_comment", "Field_Comment"),

        // Enum 相关
        "enum.Enum" to listOf("Enum", "enum", "ENUM", "EnumType", "enumType", "ENUM_TYPE", "enum_type", "Enum_Type"),
        "enum.EnumValueField" to listOf("EnumValueField", "enumValueField", "ENUM_VALUE_FIELD", "enum_value_field", "Enum_Value_Field"),
        "enum.EnumNameField" to listOf("EnumNameField", "enumNameField", "ENUM_NAME_FIELD", "enum_name_field", "Enum_Name_Field"),
        "enum.EnumItems" to listOf("EnumItems", "ENUM_ITEMS", "enumItems", "enum_items", "Enum_Items"),

        // Domain Event 相关
        "domain_event.DomainEvent" to listOf("DomainEvent", "domainEvent", "DOMAIN_EVENT", "domain_event", "Domain_Event", "Event", "EVENT", "event", "DE", "D_E", "de", "d_e"),
        "domain_event_handler.DomainEvent" to listOf("DomainEvent", "domainEvent", "DOMAIN_EVENT", "domain_event", "Domain_Event", "Event", "EVENT", "event", "DE", "D_E", "de", "d_e"),
        "domain_event.persist" to listOf("persist", "Persist", "PERSIST"),
        "domain_event_handler.persist" to listOf("persist", "Persist", "PERSIST"),

        // Domain Service 相关
        "domain_service.DomainService" to listOf("DomainService", "domainService", "DOMAIN_SERVICE", "domain_service", "Domain_Service", "Service", "SERVICE", "service", "Svc", "SVC", "svc", "DS", "D_S", "ds", "d_s"),

        // Specification 相关
        "specification.Specification" to listOf("Specification", "specification", "SPECIFICATION", "Spec", "SPEC", "spec"),

        // Factory 相关
        "factory.Factory" to listOf("Factory", "factory", "FACTORY", "Fac", "FAC", "fac"),

        // Integration Event 相关
        "integration_event.IntegrationEvent" to listOf("IntegrationEvent", "integrationEvent", "integration_event", "INTEGRATION_EVENT", "Integration_Event", "Event", "EVENT", "event", "IE", "I_E", "ie", "i_e"),
        "integration_event_handler.IntegrationEvent" to listOf("IntegrationEvent", "integrationEvent", "integration_event", "INTEGRATION_EVENT", "Integration_Event", "Event", "EVENT", "event", "IE", "I_E", "ie", "i_e"),

        // Aggregate Root 相关
        "specification.AggregateRoot" to listOf("AggregateRoot", "aggregateRoot", "aggregate_root", "AGGREGATE_ROOT", "Aggregate_Root", "Root", "ROOT", "root", "AR", "A_R", "ar", "a_r"),
        "factory.AggregateRoot" to listOf("AggregateRoot", "aggregateRoot", "aggregate_root", "AGGREGATE_ROOT", "Aggregate_Root", "Root", "ROOT", "root", "AR", "A_R", "ar", "a_r"),
        "domain_event.AggregateRoot" to listOf("AggregateRoot", "aggregateRoot", "aggregate_root", "AGGREGATE_ROOT", "Aggregate_Root", "Root", "ROOT", "root", "AR", "A_R", "ar", "a_r"),
        "domain_event_handler.AggregateRoot" to listOf("AggregateRoot", "aggregateRoot", "aggregate_root", "AGGREGATE_ROOT", "Aggregate_Root", "Root", "ROOT", "root", "AR", "A_R", "ar", "a_r"),

        // Client 相关
        "client.Client" to listOf("Client", "client", "CLIENT", "Cli", "CLI", "cli"),
        "client_handler.Client" to listOf("Client", "client", "CLIENT", "Cli", "CLI", "cli"),

        // Query 相关
        "query.Query" to listOf("Query", "query", "QUERY", "Qry", "QRY", "qry"),
        "query_handler.Query" to listOf("Query", "query", "QUERY", "Qry", "QRY", "qry"),

        // Command 相关
        "command.Command" to listOf("Command", "command", "COMMAND", "Cmd", "CMD", "cmd"),
        "command_handler.Command" to listOf("Command", "command", "COMMAND", "Cmd", "CMD", "cmd"),

        // Request 相关
        "client.Request" to listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param"),
        "client_handler.Request" to listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param"),
        "query.Request" to listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param"),
        "query_handler.Request" to listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param"),
        "command.Request" to listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param"),
        "command_handler.Request" to listOf("Request", "request", "REQUEST", "Req", "REQ", "req", "Param", "PARAM", "param"),

        // Response 相关
        "client.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "client_handler.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "query.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "query_handler.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "command.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "command_handler.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "saga.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return"),
        "saga_handler.Response" to listOf("Response", "response", "RESPONSE", "Res", "RES", "res", "ReturnType", "returnType", "RETURN_TYPE", "return_type", "Return_Type", "Return", "RETURN", "return")
    )

    private fun alias4Template(tag: String, variable: String): List<String> {
        val key = "$tag.$variable"
        return templateAliasMap[key] ?: listOf(variable)
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
    ) = Unit

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
