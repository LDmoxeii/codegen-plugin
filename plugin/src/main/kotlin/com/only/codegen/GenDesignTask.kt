package com.only.codegen

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.context.design.builders.DesignContextBuilder
import com.only.codegen.context.design.builders.KspMetadataContextBuilder
import com.only.codegen.context.design.builders.TypeMappingBuilder
import com.only.codegen.context.design.builders.UnifiedDesignBuilder
import com.only.codegen.context.design.models.AggregateInfo
import com.only.codegen.context.design.models.BaseDesign
import com.only.codegen.context.design.models.DesignElement
import com.only.codegen.generators.design.CommandGenerator
import com.only.codegen.generators.design.DesignTemplateGenerator
import com.only.codegen.generators.design.DomainEventGenerator
import com.only.codegen.generators.design.DomainEventHandlerGenerator
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackageDirectory
import com.only.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
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
            // Command 别名
            "commands" to "command",
            "command" to "command",
            "cmd" to "command",

            // Saga 别名
            "saga" to "saga",
            "sagas" to "saga",

            // Query 别名
            "queries" to "query",
            "query" to "query",
            "qry" to "query",

            // Client 别名（防腐层）
            "clients" to "client",
            "client" to "client",
            "cli" to "client",

            // Integration Event 别名
            "integration_events" to "integration_event",
            "integration_event" to "integration_event",
            "events" to "integration_event",
            "event" to "integration_event",
            "evt" to "integration_event",
            "i_e" to "integration_event",
            "ie" to "integration_event",

            // Integration Event Handler 别名
            "integration_event_handlers" to "integration_event_handler",
            "integration_event_handler" to "integration_event_handler",
            "event_handlers" to "integration_event_handler",
            "event_handler" to "integration_event_handler",
            "evt_hdl" to "integration_event_handler",
            "i_e_h" to "integration_event_handler",
            "ieh" to "integration_event_handler",
            "integration_event_subscribers" to "integration_event_handler",
            "integration_event_subscriber" to "integration_event_handler",
            "event_subscribers" to "integration_event_handler",
            "event_subscriber" to "integration_event_handler",
            "evt_sub" to "integration_event_handler",
            "i_e_s" to "integration_event_handler",
            "ies" to "integration_event_handler",

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

            // Domain Event Handler 别名
            "domain_event_handlers" to "domain_event_handler",
            "domain_event_handler" to "domain_event_handler",
            "d_e_h" to "domain_event_handler",
            "deh" to "domain_event_handler",
            "domain_event_subscribers" to "domain_event_handler",
            "domain_event_subscriber" to "domain_event_handler",
            "d_e_s" to "domain_event_handler",
            "des" to "domain_event_handler",

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
//            QueryGenerator(),             // order=10 - 生成查询
//            SagaGenerator(),              // order=10 - 生成 Saga
//            ClientGenerator(),            // order=10 - 生成客户端
//            IntegrationEventGenerator(),  // order=20 - 生成集成事件
//            DomainServiceGenerator(),     // order=20 - 生成领域服务
            DomainEventGenerator(),        // order=30 - 生成领域事件
            DomainEventHandlerGenerator()  // order=40 - 生成领域事件处理器
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

        while (designs.isNotEmpty()) {
            val design = designs.first()

            if (!generator.shouldGenerate(design, context)) {
                designs.removeFirst()
                continue
            }

            val templateContext = generator.buildContext(design, context).toMutableMap()

            val templateNodes = listOf(generator.getDefaultTemplateNode()) + context.templateNodeMap.getOrDefault(
                generator.tag,
                emptyList()
            )

            templateNodes
                .filter { templateNode ->
                    templateNode.pattern.isBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                        .test(generator.generatorName(design, context))
                }
                .forEach { templateNode ->
                    val pathNode = templateNode.deepCopy().resolve(templateContext)
                    forceRender(pathNode, resolvePackageDirectory(
                        templateContext["modulePath"].toString(),
                        concatPackage(
                            getString("basePackage"),
                            templateContext["templatePackage"].toString(),
                            templateContext["package"].toString()
                        )
                    ))
                }

            generator.onGenerated(design, context)
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
