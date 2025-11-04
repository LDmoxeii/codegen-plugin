package com.only4.codegen

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.context.aggregate.MutableAggregateContext
import com.only4.codegen.context.aggregate.builders.*
import com.only4.codegen.generators.aggregate.*
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.resolvePackageDirectory
import com.only4.codegen.template.TemplateNode
import com.only4.codegen.misc.AliasResolver
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern
import java.io.File

/**
 * 生成实体类任务
 */
import org.gradle.api.tasks.CacheableTask

@CacheableTask
open class GenAggregateTask : GenArchTask(), MutableAggregateContext {

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
        super.renderTemplate(templateNodes, parentPath)
        templateNodes.forEach { templateNode ->
            val alias = alias4Design(templateNode.tag!!)
            templateNodeMap.computeIfAbsent(alias) { mutableListOf() }.add(templateNode)
        }
    }


    private val aggregateAliases: Map<String, String> by lazy {
        AliasResolver.aggregateAliases(extension.get())
    }

    private fun alias4Design(name: String): String = aggregateAliases[name.lowercase()] ?: name

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        SqlSchemaUtils.context = this

        val engine = extension.get().generationEngine.get()
        logger.lifecycle("Codegen engine: $engine")
        if (engine.equals("v2", ignoreCase = true)) {
            genAggregateV2()
        } else {
            genEntity()
        }
    }

    private fun genEntity() {
        val context = buildGenerationContext()

        if (context.tableMap.isEmpty()) {
            logger.warn("No tables found in database")
            return
        }

        generateFiles(context)
    }

    private fun buildGenerationContext(): AggregateContext {

        val contextBuilders = listOf(
            TableContextBuilder(),          // order=10  - 表和列信息
            EntityTypeContextBuilder(),     // order=20  - 实体类型
            AnnotationContextBuilder(),     // order=20  - 注解信息
            ModuleContextBuilder(),         // order=20  - 模块信息
            RelationContextBuilder(),       // order=20  - 表关系
            EnumContextBuilder(),           // order=20  - 枚举信息
            AggregateContextBuilder(),      // order=30  - 聚合信息
            TablePackageContextBuilder(),   // order=40  - 表包信息
        )

        contextBuilders
            .sortedBy { it.order }
            .forEach { builder ->
                logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
                builder.build(this)
            }

        return this
    }

    private fun genAggregateV2() {
        val context = buildGenerationContext()

        if (context.tableMap.isEmpty()) {
            logger.warn("No tables found in database")
            return
        }

        val basePackage = getString("basePackage")
        val outputEncoding = getString("outputEncoding", "UTF-8")
        val outDomain = com.only4.codegen.engine.output.FileOutputManager(domainPath, outputEncoding)
        val outAdapter = com.only4.codegen.engine.output.FileOutputManager(adapterPath, outputEncoding)
        val outApp = com.only4.codegen.engine.output.FileOutputManager(applicationPath, outputEncoding)

        val generatedEnums = mutableSetOf<String>()

        // Enum generation via V2Render + TemplateMerger
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName] ?: return@forEach
            val aggregate = context.resolveAggregateWithModule(tableName)

            columns.forEach { column ->
                if (!SqlSchemaUtils.hasEnum(column) || SqlSchemaUtils.isIgnore(column)) return@forEach
                val enumType = SqlSchemaUtils.getType(column)
                if (!generatedEnums.add(enumType)) return@forEach

                val config = context.enumConfigMap[enumType] ?: return@forEach
                val enumItems = config.toSortedMap().map { (value, arr) ->
                    mapOf("value" to value, "name" to arr[0], "desc" to arr[1])
                }

                val defTop = com.only4.codegen.generators.aggregate.EnumGenerator().getDefaultTemplateNodes()
                val enumVars = buildMap<String, Any?> {
                    put("Enum", enumType)
                    put("Aggregate", com.only4.codegen.misc.toUpperCamelCase(aggregate) ?: aggregate)
                    put("EnumValueField", getString("enumValueField"))
                    put("EnumNameField", getString("enumNameField"))
                    put("EnumItems", enumItems)
                }
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outDomain,
                    tag = "enum",
                    genName = enumType,
                    designPackage = com.only4.codegen.misc.concatPackage(aggregate, "enums"),
                    comment = "",
                    defaultNodes = defTop,
                    templatePackageFallback = "domain.aggregates",
                    outputType = com.only4.codegen.engine.output.OutputType.ENUM,
                    vars = enumVars,
                    imports = com.only4.codegen.engine.generation.common.V2Imports.enumImports(),
                )
                context.typeMapping[enumType] = full
            }
        }

        // Ensure entities exist via legacy generator to populate typeMapping for downstream
        generateForTables(EntityGenerator(), context)

        // Factory
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table) || SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = context.entityTypeMap[tableName] ?: return@forEach
            val factoryType = "${entityType}Factory"
            if (context.typeMapping.containsKey(factoryType)) return@forEach
            val columns = context.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            if (ids.isEmpty()) return@forEach
            val fullEntityType = context.typeMapping[entityType] ?: return@forEach
            val defTop = FactoryGenerator().getDefaultTemplateNodes()
            val full = com.only4.codegen.engine.generation.common.V2Render.render(
                context = context,
                templateBaseDir = templateBaseDir,
                basePackage = basePackage,
                out = outDomain,
                tag = "factory",
                genName = factoryType,
                designPackage = com.only4.codegen.misc.concatPackage(aggregate, "factory"),
                comment = SqlSchemaUtils.getComment(table),
                defaultNodes = defTop,
                templatePackageFallback = "domain.aggregates",
                outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                vars = mapOf(
                    "Factory" to factoryType,
                    "Payload" to "${entityType}Payload",
                    "Entity" to entityType,
                    "Aggregate" to com.only4.codegen.misc.toUpperCamelCase(aggregate) ?: aggregate,
                ),
                imports = com.only4.codegen.engine.generation.common.V2Imports.factory(fullEntityType),
            )
            context.typeMapping[factoryType] = full
        }

        // Specification
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table) || SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = context.entityTypeMap[tableName] ?: return@forEach
            val specType = "${entityType}Specification"
            if (context.typeMapping.containsKey(specType)) return@forEach
            val columns = context.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            if (ids.isEmpty()) return@forEach
            val defTop = SpecificationGenerator().getDefaultTemplateNodes()
            val full = com.only4.codegen.engine.generation.common.V2Render.render(
                context = context,
                templateBaseDir = templateBaseDir,
                basePackage = basePackage,
                out = outDomain,
                tag = "specification",
                genName = specType,
                designPackage = aggregate,
                comment = SqlSchemaUtils.getComment(table),
                defaultNodes = defTop,
                templatePackageFallback = "domain.aggregates",
                outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                vars = mapOf(
                    "DEFAULT_SPEC_PACKAGE" to "specs",
                    "Specification" to specType,
                    "Entity" to entityType,
                    "Aggregate" to com.only4.codegen.misc.toUpperCamelCase(aggregate) ?: aggregate,
                ),
                imports = com.only4.codegen.engine.generation.common.V2Imports.specification(),
            )
            context.typeMapping[specType] = full
        }

        // Aggregate wrapper
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table) || SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!context.getBoolean("generateAggregate", false)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
            val entityType = context.entityTypeMap[tableName] ?: return@forEach
            val factoryType = "${entityType}Factory"
            val fullFactoryType = context.typeMapping[factoryType] ?: return@forEach
            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateName = com.only4.codegen.pebble.PebbleTemplateRenderer.renderString(
                aggregateTypeTemplate, mapOf("Entity" to entityType)
            )
            if (context.typeMapping.containsKey(aggregateName)) return@forEach
            val defTop = AggregateGenerator().getDefaultTemplateNodes()
            val full = com.only4.codegen.engine.generation.common.V2Render.render(
                context = context,
                templateBaseDir = templateBaseDir,
                basePackage = basePackage,
                out = outDomain,
                tag = "aggregate",
                genName = aggregateName,
                designPackage = aggregate,
                comment = SqlSchemaUtils.getComment(table),
                defaultNodes = defTop,
                templatePackageFallback = "domain.aggregates",
                outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                vars = mapOf(
                    "Entity" to entityType,
                    "IdentityType" to identityType,
                    "AggregateName" to aggregateName,
                    "Factory" to factoryType,
                ),
                imports = com.only4.codegen.engine.generation.common.V2Imports.aggregate(fullFactoryType),
            )
            context.typeMapping[aggregateName] = full
        }

        // Repository (adapter)
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table) || SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = context.entityTypeMap[tableName] ?: return@forEach
            val repoNameTemplate = getString("repositoryNameTemplate")
            val repoName = com.only4.codegen.pebble.PebbleTemplateRenderer.renderString(repoNameTemplate, mapOf("Aggregate" to entityType))
            if (context.typeMapping.containsKey(repoName)) return@forEach
            val columns = context.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])
            val fullRootEntityType = context.typeMapping[entityType] ?: return@forEach
            val fullIdType = context.typeMapping[identityType]
            val supportQuerydsl = getBoolean("repositorySupportQuerydsl")
            val defTop = RepositoryGenerator().getDefaultTemplateNodes()
            val full = com.only4.codegen.engine.generation.common.V2Render.render(
                context = context,
                templateBaseDir = templateBaseDir,
                basePackage = basePackage,
                out = outAdapter,
                tag = "repository",
                genName = repoName,
                designPackage = "",
                comment = "Repository for $entityType aggregate",
                defaultNodes = defTop,
                templatePackageFallback = "adapter",
                outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                vars = mapOf(
                    "supportQuerydsl" to supportQuerydsl,
                    "Aggregate" to entityType,
                    "IdentityType" to identityType,
                    "Repository" to repoName,
                ),
                imports = com.only4.codegen.engine.generation.common.V2Imports.repository(fullRootEntityType, fullIdType, supportQuerydsl),
            )
            context.typeMapping[repoName] = full
        }

        // Domain Event Handler (application)
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table) || SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach
            val events = SqlSchemaUtils.getDomainEvents(table)
            if (events.isNullOrEmpty()) return@forEach
            events.forEach { domainEventInfo ->
                val infos = domainEventInfo.split(":")
                val baseName = (com.only4.codegen.misc.toUpperCamelCase(infos[0]) ?: infos[0]).let { b ->
                    if (b.endsWith("Event") || b.endsWith("Evt")) b else "${b}DomainEvent"
                }
                val handlerName = "${baseName}Subscriber"
                if (context.typeMapping.containsKey(handlerName)) return@forEach
                val tableName = SqlSchemaUtils.getTableName(table)
                val aggregate = context.resolveAggregateWithModule(tableName)
                val defTop = DomainEventHandlerGenerator().getDefaultTemplateNodes()
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outApp,
                    tag = "domain_event_handler",
                    genName = handlerName,
                    designPackage = aggregate,
                    comment = SqlSchemaUtils.getComment(table),
                    defaultNodes = defTop,
                    templatePackageFallback = "application",
                    outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                    vars = mapOf(
                        "DomainEventHandler" to handlerName,
                        "DomainEvent" to baseName,
                    ),
                    imports = com.only4.codegen.engine.generation.common.V2Imports.domainEventHandler(context.typeMapping[baseName]),
                )
                context.typeMapping[handlerName] = full
            }
        }
    }

    private fun generateFiles(context: AggregateContext) {
        val generators = listOf(
            SchemaBaseGenerator(),           // order=10 - Schema 基类
            EnumGenerator(),                 // order=10 - 枚举类
            EnumTranslationGenerator(),      // order=20 - 枚举翻译器
            EntityGenerator(),               // order=20 - 实体类
            SpecificationGenerator(),        // order=30 - 规约类
            FactoryGenerator(),              // order=30 - 工厂类
            DomainEventGenerator(),          // order=30 - 领域事件类
            DomainEventHandlerGenerator(),   // order=30 - 领域事件处理器
            RepositoryGenerator(),           // order=30 - Repository 接口及适配器
            AggregateGenerator(),            // order=40 - 聚合封装类
            SchemaGenerator(),               // order=50 - Schema 类
        )

        generators.sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForTables(generator, context)
            }
    }

    private fun generateForTables(
        generator: AggregateTemplateGenerator,
        context: AggregateContext,
    ) {
        val tables = context.tableMap.values.toMutableList()

        with(context) {
            while (tables.isNotEmpty()) {
                val table = tables.first()

                if (!generator.shouldGenerate(table)) {
                    tables.removeFirst()
                    continue
                }

                val tableContext = generator.buildContext(table).toMutableMap().apply {
                    this["templateBaseDir"] = templateBaseDir
                }

                // 合并模板节点（先收集再组合成多套，再根据 pattern 选择）：
                // - 多个 dir/file 顶层节点可共存；每个唯一键(name+pattern)代表一套模板节点
                // - context 优先于 defaults（在文件和目录两侧都遵循此优先级）
                val genName = generator.generatorName(table)

                val ctxTop = context.templateNodeMap.getOrDefault(generator.tag, emptyList())
                val defTop = generator.getDefaultTemplateNodes()
                val selected = com.only4.codegen.template.TemplateMerger.mergeAndSelect(ctxTop, defTop, genName)

                selected.forEach { templateNode ->
                    val pathNode = templateNode.resolve(tableContext)
                    forceRender(
                        pathNode,
                        resolvePackageDirectory(
                            tableContext["modulePath"].toString(),
                            concatPackage(
                                getString("basePackage"),
                                tableContext["templatePackage"].toString(),
                                tableContext["package"].toString()
                            )
                        )
                    )
                }
                generator.onGenerated(table)
            }
        }
    }

}
