package com.only4.codegen

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.context.aggregate.MutableAggregateContext
import com.only4.codegen.context.aggregate.builders.*
import com.only4.codegen.generators.aggregate.*
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.resolvePackageDirectory
import com.only4.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern

/**
 * 生成实体类任务
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

                val tableContext = generator.buildContext(table)

                // 合并模板节点（先收集再组合成多套，再根据 pattern 选择）：
                // - 多个 dir/file 顶层节点可共存；每个唯一键(name+pattern)代表一套模板节点
                // - context 优先于 defaults（在文件和目录两侧都遵循此优先级）
                val genName = generator.generatorName(table)

                val ctxTop = context.templateNodeMap.getOrDefault(generator.tag, emptyList())
                val defTop = generator.getDefaultTemplateNodes()

                // 收集文件（来自顶层 file 以及 顶层 dir 的子项 file）
                fun collectFiles(nodes: List<TemplateNode>): List<TemplateNode> =
                    nodes.flatMap { it.collectFiles() }

                val ctxFiles = linkedMapOf<String, TemplateNode>()
                collectFiles(ctxTop).forEach { ctxFiles[it.uniqueKey()] = it }
                val defFiles = linkedMapOf<String, TemplateNode>()
                collectFiles(defTop).forEach { defFiles[it.uniqueKey()] = it }

                // pattern -> dir 的快速映射（context 优先）
                fun dirsByPattern(nodes: List<TemplateNode>): Map<String, TemplateNode> =
                    buildMap {
                        nodes.filter { it.isDirNode() }.forEach { d ->
                            if (!this.containsKey(d.pattern)) this[d.pattern] = d
                        }
                    }
                val ctxDirs = dirsByPattern(ctxTop)
                val defDirs = dirsByPattern(defTop)

                // 不同唯一键 → 一套模板
                val allKeys: Set<String> = (ctxFiles.keys + defFiles.keys).toSet()
                val groups: List<TemplateNode> = allKeys.map { key ->
                    val fileTpl = (ctxFiles[key] ?: defFiles[key])!!.deepCopy()
                    val pattern = fileTpl.pattern
                    val dirTpl = (ctxDirs[pattern] ?: defDirs[pattern])
                    if (dirTpl != null) {
                        val d = dirTpl.deepCopy()
                        // 每套模板节点只挂载本唯一键对应的文件
                        d.children = mutableListOf(fileTpl.toPathNode())
                        d
                    } else {
                        // 顶层为 file 的模板套
                        fileTpl
                    }
                }

                // 最后根据 pattern 选定最终的模板节点（只匹配顶层 TemplateNode）
                val selected = groups.filter { it.matches(genName) }

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
