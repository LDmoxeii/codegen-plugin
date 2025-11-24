package com.only4.codegen

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.context.aggregate.MutableAggregateContext
import com.only4.codegen.context.aggregate.builders.*
import com.only4.codegen.generators.aggregate.*
import com.only4.codegen.generators.aggregate.unique.UniqueQueryGenerator
import com.only4.codegen.generators.aggregate.unique.UniqueQueryHandlerGenerator
import com.only4.codegen.generators.aggregate.unique.UniqueValidatorGenerator
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.resolvePackageDirectory
import com.only4.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * 基于数据库实体的聚合生成任务
 */
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

    @Internal
    override val uniqueConstraintsMap: MutableMap<String, List<Map<String, Any?>>> = mutableMapOf()

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
        pathRenderer.renderFileSwitch = false
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

    private fun buildGenerationContext(): AggregateContext {
        val contextBuilders = listOf(
            TableContextBuilder(),           // order=10  - 表/列基础信息
            EntityTypeContextBuilder(),      // order=20  - 实体类型
            AnnotationContextBuilder(),      // order=20  - 注释/注解信息
            ModuleContextBuilder(),          // order=20  - 模块信息
            RelationContextBuilder(),        // order=20  - 关联关系
            UniqueConstraintContextBuilder(),// order=25  - 唯一约束
            EnumContextBuilder(),            // order=50  - 枚举信息
            AggregateContextBuilder(),       // order=30  - 聚合信息
            TablePackageContextBuilder(),    // order=40  - 包信息
        )

        contextBuilders.sortedBy { it.order }.forEach { builder ->
            logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
            builder.build(this)
        }
        return this
    }

    private fun generateFiles(context: AggregateContext) {
        val generators = listOf(
            SchemaBaseGenerator(),           // order=10 - Schema 基类
            EnumGenerator(),                 // order=10 - 枚举
            EnumTranslationGenerator(),      // order=20 - 枚举翻译
            EntityGenerator(),               // order=20 - 实体
            // === 基于唯一约束的生成 ===
            UniqueQueryGenerator(),          // order=22 - 唯一约束查询
            UniqueQueryHandlerGenerator(),   // order=24 - 唯一约束查询处理器
            UniqueValidatorGenerator(),      // order=28 - 唯一约束校验器
            // === 其他 ===
            SpecificationGenerator(),        // order=30 - 规范
            FactoryGenerator(),              // order=30 - 工厂
            DomainEventGenerator(),          // order=30 - 领域事件
            DomainEventHandlerGenerator(),   // order=30 - 领域事件处理器
            RepositoryGenerator(),           // order=30 - Repository 接口及实现
            AggregateGenerator(),            // order=40 - 聚合封装
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

                val tableContext = generator.buildContext(table)

                // 合并模板节点（上下文配置合并默认，再根据 pattern 选择）：
                // - 同一 dir/file 类型节点去重；每个唯一 (name+pattern) 保留一个模板节点
                // - context 优先级高于 defaults；目录和文件层级也遵循优先级合并
                val genName = generator.generatorName(table)

                val ctxTop = context.templateNodeMap.getOrDefault(generator.tag, emptyList())
                val defTop = generator.getDefaultTemplateNodes()

                val selected = TemplateNode.mergeAndSelect(ctxTop, defTop, genName)

                selected.forEach { templateNode ->
                    val pathNode = templateNode.resolve(tableContext)
                    pathRenderer.forceRender(
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

