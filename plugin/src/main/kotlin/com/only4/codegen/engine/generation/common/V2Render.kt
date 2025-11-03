package com.only4.codegen.engine.generation.common

import com.only4.codegen.context.BaseContext
import com.only4.codegen.engine.generation.design.TemplateNodeV2Context
import com.only4.codegen.engine.generation.design.TemplateNodeV2Strategy
import com.only4.codegen.engine.output.IOutputManager
import com.only4.codegen.engine.output.OutputType
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.template.TemplateNode

object V2Render {

    fun render(
        context: BaseContext,
        templateBaseDir: String,
        basePackage: String,
        out: IOutputManager,
        tag: String,
        genName: String,
        designPackage: String,
        comment: String,
        defaultNodes: List<TemplateNode>,
        templatePackageFallback: String,
        outputType: OutputType = OutputType.CONFIGURATION,
        vars: Map<String, Any?> = emptyMap(),
        imports: List<String> = emptyList(),
        extras: Map<String, Any?> = emptyMap(),
    ): String {
        val templatePkgRaw = context.templatePackage[tag] ?: templatePackageFallback
        val finalPkg = concatPackage(basePackage, templatePkgRaw, designPackage)

        val model = V2ModelBuilder.model(
            context = context,
            templateBaseDir = templateBaseDir,
            templatePackageRaw = templatePkgRaw,
            packageRaw = designPackage,
            comment = comment,
            vars = vars,
            imports = imports,
            extras = extras,
        )

        val ctxTop = context.templateNodeMap.getOrDefault(tag, emptyList())
        val strategy = TemplateNodeV2Strategy(outputType)
        val tctx = TemplateNodeV2Context(
            finalPackage = finalPkg,
            tag = tag,
            genName = genName,
            ctxTop = ctxTop,
            defTop = defaultNodes,
            model = model,
        )

        strategy.generate(tctx).forEach { out.write(it) }
        return concatPackage(finalPkg, genName)
    }
}
