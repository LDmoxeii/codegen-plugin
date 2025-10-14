package com.only.codegen

import com.only.codegen.context.design.*
import com.only.codegen.context.design.builders.*
import com.only.codegen.generators.design.*
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.resolvePackageDirectory
import com.only.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern

open class GenDesignTask : GenArchTask(), MutableDesignContext {

    @Internal
    override val designElementMap = mutableMapOf<String, MutableList<DesignElement>>()

    @Internal
    override val aggregateMetadataMap = mutableMapOf<String, AggregateMetadata>()

    @Internal
    override val entityMetadataMap = mutableMapOf<String, EntityMetadata>()

    @Internal
    override val designMap = mutableMapOf<String, MutableList<BaseDesign>>()

    @TaskAction
    override fun generate() {
        renderFileSwitch = false  // 缓存模板节点
        super.generate()          // 执行架构生成(如果配置了 archTemplate)

        genDesign()
    }

    private fun genDesign() {
        val context = buildDesignContext()

        val totalDesigns = context.designMap.values.sumOf { it.size }

        if (totalDesigns == 0) {
            logger.warn("No design elements found")
            return
        }

        logger.lifecycle("Found $totalDesigns design elements, starting generation...")
        generateDesignFiles(context)
    }

    private fun buildDesignContext(): DesignContext {
        val builders = listOf(
            JsonDesignLoader(),            // order=10  - 加载 JSON 设计文件
            KspMetadataLoader(),           // order=15  - 加载 KSP 聚合元数据
            TypeMappingBuilder(),          // order=18  - 构建类型映射 typeMapping
            UnifiedDesignBuilder()         // order=20  - 统一解析所有设计类型
        )

        builders.sortedBy { it.order }.forEach { builder ->
            logger.lifecycle("Building design context: ${builder.javaClass.simpleName}")
            builder.build(this)
        }

        return this
    }

    private fun generateDesignFiles(context: DesignContext) {
        val generators = listOf(
            CommandGenerator(),           // order=10 - 生成命令
            QueryGenerator(),             // order=10 - 生成查询
            SagaGenerator(),              // order=10 - 生成 Saga
            ClientGenerator(),            // order=10 - 生成客户端
            IntegrationEventGenerator(),  // order=20 - 生成集成事件
            DomainServiceGenerator(),     // order=20 - 生成领域服务
            DomainEventGenerator()        // order=30 - 生成领域事件
        )

        generators.sortedBy { it.order }.forEach { generator ->
            logger.lifecycle("Generating design files: ${generator.tag}")
            generateForDesigns(generator, context)
        }
    }

    private fun generateForDesigns(
        generator: DesignTemplateGenerator,
        context: DesignContext
    ) {
        // 从统一的 designMap 获取设计列表
        val designs = context.designMap[generator.tag] ?: emptyList()
        var generatedCount = 0
        var skippedCount = 0

        designs.forEach { design ->
            if (!generator.shouldGenerate(design, context)) {
                skippedCount++
                return@forEach
            }

            val templateContext = generator.buildContext(design, context).toMutableMap()
            val templateNodes = context.templateNodeMap
                .getOrDefault(generator.tag, listOf(generator.getDefaultTemplateNode()))

            templateNodes
                .filter { templateNode ->
                    templateNode.pattern.isBlank() || Pattern.compile(templateNode.pattern).asPredicate()
                        .test(generator.generatorName(design, context))
                }
                .forEach { templateNode ->
                    val pathNode = templateNode.deepCopy().resolve(templateContext)
                    val parentPath = determineParentPath(templateContext, context)
                    forceRender(pathNode, parentPath)
                    generatedCount++
                }

            generator.onGenerated(design, context)
        }

        logger.lifecycle("Generated ${generatedCount} ${generator.tag} files (skipped: $skippedCount)")
    }

    /**
     * 确定文件生成的父路径
     */
    private fun determineParentPath(
        templateContext: Map<String, Any?>,
        context: DesignContext
    ): String {
        val modulePath = templateContext["modulePath"]?.toString() ?: domainPath
        val templatePackage = templateContext["templatePackage"]?.toString() ?: ""
        val packagePath = templateContext["package"]?.toString()?.removePrefix(".") ?: ""

        val fullPackage = concatPackage(
            getString("basePackage"),
            templatePackage,
            packagePath
        )

        return resolvePackageDirectory(modulePath, fullPackage)
    }

    override fun renderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
        // 缓存模板节点
        templateNodes.forEach { templateNode ->
            val tag = templateNode.tag?.lowercase() ?: return@forEach
            templateNodeMap.computeIfAbsent(tag) { mutableListOf() }.add(templateNode)
        }
    }
}
