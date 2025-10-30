package com.only4.codegen

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.MutableDesignContext
import com.only4.codegen.context.design.builders.DesignContextBuilder
import com.only4.codegen.context.design.builders.KspMetadataContextBuilder
import com.only4.codegen.context.design.builders.TypeMappingBuilder
import com.only4.codegen.context.design.builders.UnifiedDesignBuilder
import com.only4.codegen.context.design.models.AggregateInfo
import com.only4.codegen.context.design.models.BaseDesign
import com.only4.codegen.context.design.models.DesignElement
import com.only4.codegen.generators.design.*
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.resolvePackageDirectory
import com.only4.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.extensions.core.directoryChildrenNamesHash
import java.io.File
import java.util.regex.Pattern

open class GenDesignTask : GenArchTask(), MutableDesignContext {

    @Internal
    override val designElementMap = mutableMapOf<String, MutableList<DesignElement>>()

    @Internal
    override val aggregateMap: MutableMap<String, AggregateInfo> = mutableMapOf()

    @Internal
    override val designMap = mutableMapOf<String, MutableList<BaseDesign>>()


    @Internal
    override val designTagAliasMap: Map<String, String> = mapOf(

        // Repository 别名
        "repositories" to "repository",
        "repository" to "repository",
        "repos" to "repository",
        "repo" to "repository",

        // Factory 别名
        "factories" to "factory",
        "factory" to "factory",
        "fac" to "factory",

        // Specification 别名
        "specifications" to "specification",
        "specification" to "specification",
        "specs" to "specification",
        "spec" to "specification",
        "spe" to "specification",

        // Domain Event 别名
        "domain_events" to "domain_event",
        "domain_event" to "domain_event",
        "d_e" to "domain_event",
        "de" to "domain_event",

        // Command 别名
        "commands" to "command",
        "command" to "command",
        "cmd" to "command",

        // Query 别名
        "queries" to "query",
        "query" to "query",
        "qry" to "query",

        // Client 别名（防腐层）
        "clients" to "client",
        "client" to "client",
        "cli" to "client",

        // Saga 别名
        "saga" to "saga",
        "sagas" to "saga",

        // Validator 别名
        "validators" to "validator",
        "validator" to "validator",
        "validater" to "validator",
        "validate" to "validator",

        // Integration Event 别名
        "integration_events" to "integration_event",
        "integration_event" to "integration_event",
        "events" to "integration_event",
        "event" to "integration_event",
        "evt" to "integration_event",
        "i_e" to "integration_event",
        "ie" to "integration_event",

        // Domain Service 别名
        "domain_service" to "domain_service",
        "domain_services" to "domain_service",
        "service" to "domain_service",
        "svc" to "domain_service"
    )


    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()

        genDesign()
    }

    private fun genDesign() {
        val metadataPath = resolveMetadataPath()

        if (!metadataPath.exists()) {
            return
        }

        val context = buildDesignContext(metadataPath.absolutePath)

        val totalDesigns = context.designMap.values.sumOf { it.size }

        if (totalDesigns == 0) {
            return
        }

        generateDesignFiles(context)
    }

    private fun resolveMetadataPath(): File {
        val domainModulePath = File(getString("domainModulePath"))
        val domainKspPath = File(domainModulePath, "build/generated/ksp/main/resources/metadata")
        return domainKspPath
    }

    private fun buildDesignContext(metadataPath: String): DesignContext {
        val builders = listOf(
            DesignContextBuilder(),                            // order=10  - 加载 JSON 设计文件
            KspMetadataContextBuilder(metadataPath),           // order=15  - 加载 KSP 聚合元数据
            TypeMappingBuilder(),                       // order=18  - 构建类型映射 typeMapping
            UnifiedDesignBuilder()                      // order=20  - 统一解析所有设计类型
        )

        builders.sortedBy { it.order }.forEach { builder ->
            builder.build(this)
        }

        return this
    }

    private fun generateDesignFiles(context: DesignContext) {
        val generators = listOf(
            CommandGenerator(),           // order=10 - 生成命令
            QueryGenerator(),             // order=10 - 生成查询
            DomainEventGenerator(),        // order=10 - 生成领域事件
            DomainEventHandlerGenerator(),  // order=20 - 生成领域事件处理器
            QueryHandlerGenerator(),        // order=20 - 生成查询处理器
            ValidatorGenerator()            // order=10 - 生成校验器
        )

        generators.sortedBy { it.order }.forEach { generator ->
            generateForDesigns(generator, context)
        }
    }

    private fun generateForDesigns(
        generator: DesignTemplateGenerator,
        context: DesignContext
    ) {
        val designs = context.designMap[generator.tag]?.toMutableList() ?: mutableListOf()

        with(context) {
            while (designs.isNotEmpty()) {
                val design = designs.first()

                if (!generator.shouldGenerate(design)) {
                    designs.removeFirst()
                    continue
                }

                val templateContext = generator.buildContext(design).toMutableMap()

                // 合并模板节点（先收集再组合成多套，再根据 pattern 选择）：
                // - 多个 dir/file 顶层节点可共存；每个唯一键(name+pattern)代表一套模板节点
                // - context 优先于 defaults（在文件和目录两侧都遵循此优先级）
                val genName = generator.generatorName(design)

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
                    val pathNode = templateNode.resolve(templateContext)
                    forceRender(
                        pathNode, resolvePackageDirectory(
                            templateContext["modulePath"].toString(),
                            concatPackage(
                                getString("basePackage"),
                                templateContext["templatePackage"].toString(),
                                templateContext["package"].toString()
                            )
                        )
                    )
                }

                generator.onGenerated(design)
            }
        }
    }

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        super.renderTemplate(templateNodes, parentPath)
        templateNodes.forEach { templateNode ->
            val tag = templateNode.tag?.lowercase()?.let { designTagAliasMap[it] ?: it } ?: return@forEach
            templateNodeMap.computeIfAbsent(tag) { mutableListOf() }.add(templateNode)
        }
    }
}
