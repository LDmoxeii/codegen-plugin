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
import com.only4.codegen.misc.AliasResolver
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import com.only4.codegen.misc.resolvePackageDirectory
import com.only4.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.regex.Pattern
import org.gradle.api.tasks.OutputDirectory

import org.gradle.api.tasks.CacheableTask

@CacheableTask
open class GenDesignTask : GenArchTask(), MutableDesignContext {

    @Internal
    override val designElementMap = mutableMapOf<String, MutableList<DesignElement>>()

    @Internal
    override val aggregateMap: MutableMap<String, AggregateInfo> = mutableMapOf()

    @Internal
    override val designMap = mutableMapOf<String, MutableList<BaseDesign>>()


    @get:Internal
    override val designTagAliasMap: Map<String, String>
        get() = AliasResolver.designAliases(extension.get())


    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        val engine = extension.get().generationEngine.get()
        logger.lifecycle("Codegen engine: $engine")

        if (engine.equals("v2", ignoreCase = true)) {
            genDesignV2()
        } else {
            genDesign()
        }
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
            CommandGenerator(),             // order=10 - 生成命令
            QueryGenerator(),               // order=10 - 生成查询
            ClientGenerator(),              // order=10 - 生成分布式客户端（防腐层）
            DomainEventGenerator(),         // order=10 - 生成领域事件
            DomainEventHandlerGenerator(),  // order=20 - 生成领域事件处理器
            QueryHandlerGenerator(),        // order=20 - 生成查询处理器
            ClientHandlerGenerator(),       // order=20 - 生成分布式客户端处理器
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

                val templateContext = generator.buildContext(design).toMutableMap().apply {
                    this["templateBaseDir"] = templateBaseDir
                }

                // 合并模板节点（先收集再组合成多套，再根据 pattern 选择）：
                // - 多个 dir/file 顶层节点可共存；每个唯一键(name+pattern)代表一套模板节点
                // - context 优先于 defaults（在文件和目录两侧都遵循此优先级）
                val genName = generator.generatorName(design)

                val ctxTop = context.templateNodeMap.getOrDefault(generator.tag, emptyList())
                val defTop = generator.getDefaultTemplateNodes()

                val selected = com.only4.codegen.template.TemplateMerger.mergeAndSelect(ctxTop, defTop, genName)

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

    private fun genDesignV2() {
        val metadataPath = resolveMetadataPath()
        if (!metadataPath.exists()) return

        val context = buildDesignContext(metadataPath.absolutePath)

        val basePackage = getString("basePackage")
        val outputEncoding = getString("outputEncoding", "UTF-8")
        val out = com.only4.codegen.engine.output.FileOutputManager(applicationPath, outputEncoding)

        // Validator
        run {
            val list = context.designMap["validator"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.V2ValidatorStrategy()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val className = com.only4.codegen.misc.toUpperCamelCase(design.name) ?: design.name
                val v2ctx = com.only4.codegen.engine.generation.design.DesignV2Context(
                    basePackage, applicationPath, design.`package`, className, design.desc, outputEncoding
                )
                strategy.generate(v2ctx).forEach { out.write(it) }
            }
        }

        // Command
        run {
            val list = context.designMap["command"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.V2CommandStrategy()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cmd")) design.name else "${design.name}Cmd"
                val className = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val v2ctx = com.only4.codegen.engine.generation.design.DesignV2Context(
                    basePackage, applicationPath, design.`package`, className, design.desc, outputEncoding
                )
                strategy.generate(v2ctx).forEach { out.write(it) }
            }
        }

        // Query
        run {
            val list = context.designMap["query"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.V2QueryStrategy()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Qry")) design.name else "${design.name}Qry"
                val className = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val v2ctx = com.only4.codegen.engine.generation.design.DesignV2Context(
                    basePackage, applicationPath, design.`package`, className, design.desc, outputEncoding
                )
                strategy.generate(v2ctx).forEach { out.write(it) }
            }
        }
    }
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val designInputFiles: FileCollection
        get() = extension.get().designFiles
}
