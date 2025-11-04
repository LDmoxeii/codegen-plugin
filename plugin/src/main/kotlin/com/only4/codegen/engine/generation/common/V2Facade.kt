package com.only4.codegen.engine.generation.common

import com.only4.codegen.context.BaseContext
import com.only4.codegen.engine.output.IOutputManager
import com.only4.codegen.engine.output.OutputType
import com.only4.codegen.template.TemplateNode

object V2Facade {

    fun render(
        context: BaseContext,
        templateBaseDir: String,
        basePackage: String,
        out: IOutputManager,
        tag: String,
        genName: String,
        designPackage: String,
        comment: String,
        defNodesProvider: () -> List<TemplateNode>,
        importsProvider: () -> List<String> = { emptyList() },
        varsProvider: () -> Map<String, Any?> = { emptyMap() },
        templatePackageFallback: String,
        outputType: OutputType = OutputType.CONFIGURATION,
    ): String {
        val defaults = defNodesProvider.invoke()
        val imports = importsProvider.invoke()
        val vars = varsProvider.invoke()
        return V2Render.render(
            context = context,
            templateBaseDir = templateBaseDir,
            basePackage = basePackage,
            out = out,
            tag = tag,
            genName = genName,
            designPackage = designPackage,
            comment = comment,
            defaultNodes = defaults,
            templatePackageFallback = templatePackageFallback,
            outputType = outputType,
            vars = vars,
            imports = imports,
        )
    }
}

