package com.only.codegen

import com.only.codegen.context.EntityContext
import com.only.codegen.context.MutableEntityContext
import com.only.codegen.context.builders.*
import com.only.codegen.generators.EntityGenerator
import com.only.codegen.generators.EnumGenerator
import com.only.codegen.generators.TemplateGenerator
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackage
import com.only.codegen.misc.resolvePackageDirectory
import com.only.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 生成实体类任务
 */
open class GenEntityTask : GenArchTask(), MutableEntityContext {

    companion object {
        private val DEFAULT_ENTITY_IMPORTS = listOf(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
            "jakarta.persistence.*",
            "org.hibernate.annotations.DynamicInsert",
            "org.hibernate.annotations.DynamicUpdate",
            "org.hibernate.annotations.Fetch",
            "org.hibernate.annotations.FetchMode",
            "org.hibernate.annotations.GenericGenerator",
            "org.hibernate.annotations.SQLDelete",
            "org.hibernate.annotations.Where",
        )
    }

    @Internal
    override val dbType: String = "dbType"

    @get:Internal
    override var aggregatesPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.$AGGREGATE_PACKAGE"
        ).also { field = it }

    @get:Internal
    override var schemaPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.${getString("entitySchemaOutputPackage").takeIf { it.isNotBlank() } ?: "domain._share.meta"}"
        ).also { field = it }

    @get:Internal
    override var subscriberPath: String = ""
        get() = field.takeIf { it.isNotBlank() } ?: resolvePackageDirectory(
            domainPath,
            "${getString("basePackage")}.$DOMAIN_EVENT_SUBSCRIBER_PACKAGE"
        ).also { field = it }

    @get:Internal
    override val aggregatesPackage: String by lazy {
        resolvePackage("${aggregatesPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val schemaPackage: String by lazy {
        resolvePackage("${schemaPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val subscriberPackage: String by lazy {
        resolvePackage("${subscriberPath}${File.separator}X.kt")
            .substring(getString("basePackage").length + 1)
    }

    @get:Internal
    override val entityClassExtraImports: List<String> by lazy {
        buildList {
            addAll(DEFAULT_ENTITY_IMPORTS)
            val extraImports = getString("entityClassExtraImports")

            if (extraImports.isNotEmpty()) {
                addAll(
                    extraImports.split(";")
                        .asSequence()
                        .map { it.trim().replace(Regex(PATTERN_LINE_BREAK), "") }
                        .map { if (it.startsWith("import ")) it.substring(6).trim() else it }
                        .filter { it.isNotBlank() }
                        .toList()
                )
            }
        }.distinct()
    }

    @Internal
    override val tableMap: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    @Internal
    override val columnsMap: MutableMap<String, List<Map<String, Any?>>> = mutableMapOf()

    @Internal
    override val relationsMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Internal
    override val tablePackageMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val entityTypeMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val tableModuleMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val tableAggregateMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val annotationsMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Internal
    override val enumConfigMap: MutableMap<String, Map<Int, Array<String>>> = mutableMapOf()

    @Internal
    override val enumPackageMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val enumTableNameMap: MutableMap<String, String> = mutableMapOf()
    override fun resolveAggregateWithModule(tableName: String): String {
        val module = tableModuleMap[tableName]
        return if (!(module.isNullOrBlank())) {
            concatPackage(module, tableAggregateMap[tableName]!!)
        } else {
            tableAggregateMap[tableName]!!
        }
    }

    override fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        templateNodes.forEach { templateNode ->
            val alias = alias4Design(templateNode.tag!!)
            when (alias) {
                "aggregate" -> aggregatesPath = parentPath
                "schema_base" -> schemaPath = parentPath
                "domain_event_handler" -> subscriberPath = parentPath
            }
            templateNodeMap.computeIfAbsent(alias) { mutableListOf() }.add(templateNode)
        }
    }


    private fun alias4Design(name: String): String = when (name.lowercase()) {
        "entity", "aggregate", "entities", "aggregates" -> "aggregate"
        "schema", "schemas" -> "schema"
        "enum", "enums" -> "enum"
        "enumitem", "enum_item" -> "enum_item"
        "factories", "factory", "fac" -> "factory"
        "specifications", "specification", "specs", "spec", "spe" -> "specification"
        "domain_events", "domain_event", "d_e", "de" -> "domain_event"
        "domain_event_handlers", "domain_event_handler", "d_e_h", "deh",
        "domain_event_subscribers", "domain_event_subscriber", "d_e_s", "des",
            -> "domain_event_handler"

        "domain_service", "service", "svc" -> "domain_service"
        else -> name
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        SqlSchemaUtils.context = this

        genEntity()
    }

    private fun genEntity() {
        val context = buildGenerationContext()

        if (context.tableMap.isEmpty()) {
            logger.warn("No tables found in database")
            return
        }

        generateFiles(context)
    }

    private fun buildGenerationContext(): EntityContext {

        val contextBuilders = listOf(
            TableContextBuilder(),
            EntityTypeContextBuilder(),
            ModuleContextBuilder(),
            AggregateContextBuilder(),
            TablePackageContextBuilder(),
            AnnotationContextBuilder(),
            RelationContextBuilder(),
            EnumContextBuilder(),
            EnumPackageContextBuilder()
        )

        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
                builder.build(this)
            }

        return this
    }

    private fun generateFiles(context: EntityContext) {
        val generators = listOf(
            EnumGenerator(),
            EntityGenerator(),
        )

        generators.sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForTables(generator, context)
            }
    }

    private fun generateForTables(
        generator: TemplateGenerator,
        context: EntityContext
    ) {
        val tables = context.tableMap.values.toMutableList()

        while (tables.isNotEmpty()) {
            val table = tables.first()

            if (!generator.shouldGenerate(table, context)) {
                tables.removeFirst()
                continue
            }

            val tableContext = generator.buildContext(table, context)
            val templateNodes = context.templateNodeMap
                .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

            templateNodes.forEach { templateNode ->
                val pathNode = templateNode.deepCopy().resolve(tableContext)
                forceRender(
                    pathNode,
                    resolvePackageDirectory(
                        project.projectDir.absolutePath,
                        concatPackage(
                            getString("basePackage"),
                            tableContext["templatePackage"].toString(),
                            tableContext["package"].toString()
                        )
                    )
                )
            }
            generator.onGenerated(table, context)
        }
    }

}
