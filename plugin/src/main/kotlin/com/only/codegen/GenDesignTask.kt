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
//            DomainEventGenerator()        // order=30 - 生成领域事件
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
            val tag = templateNode.tag?.lowercase() ?: return@forEach
            templateNodeMap.computeIfAbsent(tag) { mutableListOf() }.add(templateNode)
        }
    }
}
