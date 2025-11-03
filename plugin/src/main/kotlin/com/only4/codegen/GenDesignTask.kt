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

        // Validator (v2 via TemplateNodeV2Strategy + legacy pattern merging)
        run {
            val list = context.designMap["validator"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.TemplateNodeV2Strategy(
                com.only4.codegen.engine.output.OutputType.CONFIGURATION
            )
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val className = com.only4.codegen.misc.toUpperCamelCase(design.name) ?: design.name
                val templatePkgRaw = context.templatePackage["validator"] ?: "application.validater"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkgRaw, design.`package`)
                val model = com.only4.codegen.engine.generation.common.V2ModelBuilder.model(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    templatePackageRaw = templatePkgRaw,
                    packageRaw = design.`package`,
                    comment = design.desc,
                    vars = mapOf(
                        "Validator" to className,
                        "ValueType" to "Long",
                    )
                )
                val ctxTop = context.templateNodeMap.getOrDefault("validator", emptyList())
                val defTop = ValidatorGenerator().getDefaultTemplateNodes()
                val tctx = com.only4.codegen.engine.generation.design.TemplateNodeV2Context(
                    finalPackage = finalPkg,
                    tag = "validator",
                    genName = className,
                    ctxTop = ctxTop,
                    defTop = defTop,
                    model = model,
                )
                strategy.generate(tctx).forEach { outApp.write(it) }
                context.typeMapping[className] = com.only4.codegen.misc.concatPackage(finalPkg, className)
            }
        }

        // Command (v2 via TemplateNodeV2Strategy + ImportManager)
        run {
            val list = context.designMap["command"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.TemplateNodeV2Strategy(
                com.only4.codegen.engine.output.OutputType.CONFIGURATION
            )
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cmd")) design.name else "${design.name}Cmd"
                val className = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val templatePkgRaw = context.templatePackage["command"] ?: "application.command"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkgRaw, design.`package`)
                val imports = com.only4.codegen.manager.CommandImportManager().apply { addBaseImports() }.toImportLines()
                val model = com.only4.codegen.engine.generation.common.V2ModelBuilder.model(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    templatePackageRaw = templatePkgRaw,
                    packageRaw = design.`package`,
                    comment = design.desc,
                    vars = mapOf("Command" to className),
                    imports = imports,
                )
                val ctxTop = context.templateNodeMap.getOrDefault("command", emptyList())
                val defTop = CommandGenerator().getDefaultTemplateNodes()
                val tctx = com.only4.codegen.engine.generation.design.TemplateNodeV2Context(
                    finalPackage = finalPkg,
                    tag = "command",
                    genName = className,
                    ctxTop = ctxTop,
                    defTop = defTop,
                    model = model,
                )
                strategy.generate(tctx).forEach { outApp.write(it) }
                context.typeMapping[className] = com.only4.codegen.misc.concatPackage(finalPkg, className)
            }
        }

        // Query (v2 via new abstraction + legacy pattern selection using TemplateMerger)
        run {
            val list = context.designMap["query"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.TemplateNodeV2Strategy(
                com.only4.codegen.engine.output.OutputType.CONFIGURATION
            )
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val base = com.only4.codegen.misc.toUpperCamelCase(
                    if (design.name.endsWith("Qry")) design.name else "${design.name}Qry"
                ) ?: design.name
                val templatePkgRaw = context.templatePackage["query"] ?: "application.query"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkgRaw, design.`package`)
                val qImports = com.only4.codegen.manager.QueryImportManager().apply { addBaseImports() }.toImportLines()
                val model = buildMap<String, Any?> {
                    putAll(context.baseMap)
                    put("templatePackage", com.only4.codegen.misc.refPackage(templatePkgRaw))
                    put("package", com.only4.codegen.misc.refPackage(design.`package`))
                    put("Query", base)
                    put("Comment", design.desc)
                    put("imports", qImports)
                    put("templateBaseDir", templateBaseDir)
                }
                val ctxTop = context.templateNodeMap.getOrDefault("query", emptyList())
                val defTop = QueryGenerator().getDefaultTemplateNodes()
                val ctx = com.only4.codegen.engine.generation.design.TemplateNodeV2Context(
                    finalPackage = finalPkg,
                    tag = "query",
                    genName = base,
                    ctxTop = ctxTop,
                    defTop = defTop,
                    model = model,
                )
                strategy.generate(ctx).forEach { outApp.write(it) }
                context.typeMapping[base] = com.only4.codegen.misc.concatPackage(finalPkg, base)
            }
        }

        // Client (v2 via TemplateNodeV2Strategy + ImportManager) — used by client_handler dependency
        run {
            val list = context.designMap["client"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.TemplateNodeV2Strategy(
                com.only4.codegen.engine.output.OutputType.DTO
            )
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cli")) design.name else "${design.name}Cli"
                val className = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val templatePkgRaw = context.templatePackage["client"] ?: "application.client"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkgRaw, design.`package`)
                val imports = com.only4.codegen.manager.ClientImportManager().apply { addBaseImports() }.toImportLines()
                val model = com.only4.codegen.engine.generation.common.V2ModelBuilder.model(
                    context = context,
                    templateBaseDir = templateBaseDir,
                    templatePackageRaw = templatePkgRaw,
                    packageRaw = design.`package`,
                    comment = design.desc,
                    vars = mapOf("Client" to className),
                    imports = imports,
                )
                val ctxTop = context.templateNodeMap.getOrDefault("client", emptyList())
                val defTop = ClientGenerator().getDefaultTemplateNodes()
                val tctx = com.only4.codegen.engine.generation.design.TemplateNodeV2Context(
                    finalPackage = finalPkg,
                    tag = "client",
                    genName = className,
                    ctxTop = ctxTop,
                    defTop = defTop,
                    model = model,
                )
                strategy.generate(tctx).forEach { outApp.write(it) }
                context.typeMapping[className] = com.only4.codegen.misc.concatPackage(finalPkg, className)
            }
        }

        // Query Handler (adapter) — v2 abstraction + legacy pattern selection using TemplateMerger
        run {
            val list = context.designMap["query_handler"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.TemplateNodeV2Strategy(
                com.only4.codegen.engine.output.OutputType.SERVICE
            )
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val base = com.only4.codegen.misc.toUpperCamelCase(
                    if (design.name.endsWith("Qry")) design.name else "${design.name}Qry"
                ) ?: design.name
                val handlerName = "${base}Handler"
                val templatePkgRaw = context.templatePackage["query_handler"] ?: "adapter"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkgRaw, design.`package`)
                val inferred = com.only4.codegen.manager.QueryHandlerImportManager.inferQueryType(design.name)
                val imports = mutableListOf<String>().apply {
                    add("org.springframework.stereotype.Service")
                    when (inferred) {
                        com.only4.codegen.manager.QueryHandlerImportManager.QueryType.SINGLE -> add("com.only4.cap4k.ddd.core.application.query.Query")
                        com.only4.codegen.manager.QueryHandlerImportManager.QueryType.LIST -> add("com.only4.cap4k.ddd.core.application.query.ListQuery")
                        com.only4.codegen.manager.QueryHandlerImportManager.QueryType.PAGE -> {
                            add("com.only4.cap4k.ddd.core.application.query.PageQuery")
                            add("com.only4.cap4k.ddd.core.share.PageData")
                        }
                    }
                    context.typeMapping[base]?.let { add(it) }
                }
                val model = buildMap<String, Any?> {
                    putAll(context.baseMap)
                    put("templatePackage", com.only4.codegen.misc.refPackage(templatePkgRaw))
                    put("package", com.only4.codegen.misc.refPackage(design.`package`))
                    put("QueryHandler", handlerName)
                    put("Query", base)
                    put("Comment", design.desc)
                    put("imports", imports)
                    put("templateBaseDir", templateBaseDir)
                }
                val ctxTop = context.templateNodeMap.getOrDefault("query_handler", emptyList())
                val defTop = QueryHandlerGenerator().getDefaultTemplateNodes()
                val ctx = com.only4.codegen.engine.generation.design.TemplateNodeV2Context(
                    finalPackage = finalPkg,
                    tag = "query_handler",
                    genName = handlerName,
                    ctxTop = ctxTop,
                    defTop = defTop,
                    model = model,
                )
                strategy.generate(ctx).forEach { outAdapter.write(it) }
                context.typeMapping[handlerName] = com.only4.codegen.misc.concatPackage(finalPkg, handlerName)
            }
        }

        // Client Handler (adapter)
        run {
            val list = context.designMap["client_handler"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.V2ClientHandlerStrategy()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.CommonDesign) return@forEach
                val raw = if (design.name.endsWith("Cli")) design.name else "${design.name}Cli"
                val base = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val className = "${base}Handler"
                val templatePkg = context.templatePackage["client_handler"] ?: "adapter"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkg, design.`package`)
                val clientFull = context.typeMapping[base]
                val imports = mutableListOf(
                    "org.springframework.stereotype.Service",
                    "com.only4.cap4k.ddd.core.application.RequestHandler",
                )
                if (clientFull != null) imports.add(clientFull)
                val hctx = com.only4.codegen.engine.generation.design.HandlerV2Context(
                    finalPackage = finalPkg,
                    className = className,
                    description = design.desc,
                    imports = imports,
                    implements = ": RequestHandler<${base}.Request, ${base}.Response>",
                    methodSignature = "override fun exec(request: ${base}.Request): ${base}.Response",
                    methodBody = "return ${base}.Response()"
                )
                strategy.generate(hctx).forEach { outAdapter.write(it) }
                context.typeMapping[className] = com.only4.codegen.misc.concatPackage(finalPkg, className)
            }
        }

        // Domain Event Handler (application)
        run {
            val list = context.designMap["domain_event_handler"] ?: emptyList()
            val strategy = com.only4.codegen.engine.generation.design.V2DomainEventHandlerStrategy()
            list.forEach { design ->
                if (design !is com.only4.codegen.context.design.models.DomainEventDesign) return@forEach
                var raw = design.name
                if (!raw.endsWith("Evt") && !raw.endsWith("Event")) raw += "DomainEvent"
                val base = com.only4.codegen.misc.toUpperCamelCase(raw) ?: raw
                val className = "${base}Subscriber"
                val templatePkg = context.templatePackage["domain_event_handler"] ?: "application"
                val finalPkg = com.only4.codegen.misc.concatPackage(basePackage, templatePkg, design.`package`, "events")
                val eventFull = context.typeMapping[base]
                val imports = mutableListOf(
                    "org.springframework.context.event.EventListener",
                    "org.springframework.stereotype.Service",
                )
                if (eventFull != null) imports.add(eventFull)
                val hctx = com.only4.codegen.engine.generation.design.HandlerV2Context(
                    finalPackage = finalPkg,
                    className = className,
                    description = design.desc,
                    imports = imports,
                    implements = "",
                    methodSignature = "@EventListener(${base}::class) fun on(event: ${base})",
                    methodBody = "// TODO"
                )
                strategy.generate(hctx).forEach { outApp.write(it) }
                context.typeMapping[className] = com.only4.codegen.misc.concatPackage(finalPkg, className)
            }
        }
    }
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val designInputFiles: FileCollection
        get() = extension.get().designFiles
}
