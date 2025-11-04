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
        val outApp = com.only4.codegen.engine.output.FileOutputManager(applicationPath, outputEncoding)
        val outAdapter = com.only4.codegen.engine.output.FileOutputManager(adapterPath, outputEncoding)
        val outDomain = com.only4.codegen.engine.output.FileOutputManager(domainPath, outputEncoding)

        // Validator (v2 via V2Facade)
        run {
            val list = context.designMap["validator"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val className = com.only4.codegen.misc.toUpperCamelCase(design.name) ?: design.name
                val full = com.only4.codegen.engine.generation.common.V2Facade.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outApp,
                    tag = "validator",
                    genName = className,
                    designPackage = design.`package`,
                    comment = design.desc,
                    defNodesProvider = { ValidatorGenerator().getDefaultTemplateNodes() },
                    varsProvider = { mapOf(
                        "Validator" to className,
                        "ValueType" to "Long",
                    ) },
                    templatePackageFallback = "application.validater",
                    outputType = com.only4.codegen.engine.output.OutputType.CONFIGURATION,
                )
                context.typeMapping[className] = full
            }
        }

        // Command (v2 via V2Facade + ImportManager)
        run {
            val list = context.designMap["command"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cmd")) design.name else "${design.name}Cmd"
                val className = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val full = com.only4.codegen.engine.generation.common.V2Facade.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outApp,
                    tag = "command",
                    genName = className,
                    designPackage = design.`package`,
                    comment = design.desc,
                    defNodesProvider = { CommandGenerator().getDefaultTemplateNodes() },
                    importsProvider = { com.only4.codegen.engine.generation.common.V2Imports.command() },
                    varsProvider = { mapOf("Command" to className) },
                    templatePackageFallback = "application.command",
                    outputType = com.only4.codegen.engine.output.OutputType.CONFIGURATION,
                )
                context.typeMapping[className] = full
            }
        }

        // Query (v2 via V2Render + legacy pattern selection)
        run {
            val list = context.designMap["query"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val base = com.only4.codegen.misc.toUpperCamelCase(
                    if (design.name.endsWith("Qry")) design.name else "${design.name}Qry"
                ) ?: design.name
                val defTop = QueryGenerator().getDefaultTemplateNodes()
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outApp,
                    tag = "query",
                    genName = base,
                    designPackage = design.`package`,
                    comment = design.desc,
                    defaultNodes = defTop,
                    templatePackageFallback = "application.query",
                    outputType = com.only4.codegen.engine.output.OutputType.CONFIGURATION,
                    vars = mapOf("Query" to base),
                    imports = com.only4.codegen.engine.generation.common.V2Imports.query(design.name),
                )
                context.typeMapping[base] = full
            }
        }

        // Client (v2 via V2Facade + ImportManager) — used by client_handler dependency
        run {
            val list = context.designMap["client"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cli")) design.name else "${design.name}Cli"
                val className = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val full = com.only4.codegen.engine.generation.common.V2Facade.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outApp,
                    tag = "client",
                    genName = className,
                    designPackage = design.`package`,
                    comment = design.desc,
                    defNodesProvider = { ClientGenerator().getDefaultTemplateNodes() },
                    importsProvider = { com.only4.codegen.engine.generation.common.V2Imports.client() },
                    varsProvider = { mapOf("Client" to className) },
                    templatePackageFallback = "application.client",
                    outputType = com.only4.codegen.engine.output.OutputType.DTO,
                )
                context.typeMapping[className] = full
            }
        }

        // Domain Event (definition) — v2 via V2Render + DomainEventImportManager
        run {
            val list = context.designMap["domain_event"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.DomainEventDesign) return@forEach
                var raw = design.name
                if (!raw.endsWith("Evt") && !raw.endsWith("Event")) raw += "DomainEvent"
                val eventName = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val defTop = DomainEventGenerator().getDefaultTemplateNodes()
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outDomain,
                    tag = "domain_event",
                    genName = eventName,
                    designPackage = com.only4.codegen.misc.concatPackage(design.`package`, "events"),
                    comment = design.desc,
                    defaultNodes = defTop,
                    templatePackageFallback = "",
                    outputType = com.only4.codegen.engine.output.OutputType.CONFIGURATION,
                    vars = mapOf(
                        "DomainEvent" to eventName,
                        "Entity" to design.entity,
                        "Aggregate" to design.aggregate,
                        "persist" to design.persist.toString(),
                    ),
                    imports = com.only4.codegen.engine.generation.common.V2Imports.domainEvent(context.typeMapping[design.entity]),
                )
                context.typeMapping[eventName] = full
            }
        }

        // Query Handler (adapter) — v2 via V2Render + legacy pattern selection
        run {
            val list = context.designMap["query_handler"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val base = com.only4.codegen.misc.toUpperCamelCase(
                    if (design.name.endsWith("Qry")) design.name else "${design.name}Qry"
                ) ?: design.name
                val handlerName = "${base}Handler"
                val defTop = QueryHandlerGenerator().getDefaultTemplateNodes()
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outAdapter,
                    tag = "query_handler",
                    genName = handlerName,
                    designPackage = design.`package`,
                    comment = design.desc,
                    defaultNodes = defTop,
                    templatePackageFallback = "adapter",
                    outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                    vars = mapOf(
                        "QueryHandler" to handlerName,
                        "Query" to base,
                    ),
                    imports = com.only4.codegen.engine.generation.common.V2Imports.queryHandler(design.name, context.typeMapping[base]),
                )
                context.typeMapping[handlerName] = full
            }
        }

        // Client Handler (adapter) — v2 via V2Render + ImportManager
        run {
            val list = context.designMap["client_handler"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cli")) design.name else "${design.name}Cli"
                val base = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val className = "${base}Handler"
                val defTop = ClientHandlerGenerator().getDefaultTemplateNodes()
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outAdapter,
                    tag = "client_handler",
                    genName = className,
                    designPackage = design.`package`,
                    comment = design.desc,
                    defaultNodes = defTop,
                    templatePackageFallback = "adapter",
                    outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                    vars = mapOf("Client" to base),
                    imports = com.only4.codegen.engine.generation.common.V2Imports.clientHandler(context.typeMapping[base]),
                )
                context.typeMapping[className] = full
            }
        }

        // Domain Event Handler (application) — v2 via V2Render + ImportManager
        run {
            val list = context.designMap["domain_event_handler"] ?: emptyList()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.DomainEventDesign) return@forEach
                var raw = design.name
                if (!raw.endsWith("Evt") && !raw.endsWith("Event")) raw += "DomainEvent"
                val base = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val className = "${base}Subscriber"
                val defTop = DomainEventHandlerGenerator().getDefaultTemplateNodes()
                val full = com.only4.codegen.engine.generation.common.V2Render.render(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    basePackage = basePackage,
                    out = outApp,
                    tag = "domain_event_handler",
                    genName = className,
                    designPackage = com.only4.codegen.misc.concatPackage(design.`package`, "events"),
                    comment = design.desc,
                    defaultNodes = defTop,
                    templatePackageFallback = "application",
                    outputType = com.only4.codegen.engine.output.OutputType.SERVICE,
                    vars = mapOf(
                        "DomainEventHandler" to className,
                        "DomainEvent" to base,
                    ),
                    imports = com.only4.codegen.engine.generation.common.V2Imports.domainEventHandler(context.typeMapping[base]),
                )
                context.typeMapping[className] = full
            }
        }
    }
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val designInputFiles: FileCollection
        get() = extension.get().designFiles
}
