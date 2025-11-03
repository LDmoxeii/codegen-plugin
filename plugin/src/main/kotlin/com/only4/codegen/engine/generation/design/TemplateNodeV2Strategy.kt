package com.only4.codegen.engine.generation.design

import com.only4.codegen.engine.generation.IGenerationStrategy
import com.only4.codegen.engine.output.GenerationResult
import com.only4.codegen.engine.output.OutputType
import com.only4.codegen.template.TemplateMerger

class TemplateNodeV2Strategy(
    private val outputType: OutputType = OutputType.CONFIGURATION
) : IGenerationStrategy<TemplateNodeV2Context> {
    override fun generate(context: TemplateNodeV2Context): List<GenerationResult> {
        val selected = TemplateMerger.mergeAndSelect(context.ctxTop, context.defTop, context.genName)
        return selected.map { tpl ->
            val resolved = tpl.resolve(context.model)
            GenerationResult(
                fileName = resolved.name ?: "${context.genName}.kt",
                content = resolved.data.orEmpty(),
                packageName = context.finalPackage,
                type = outputType,
            )
        }
    }
}

