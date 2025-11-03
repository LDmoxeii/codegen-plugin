package com.only4.codegen.engine.generation.aggregate

import com.only4.codegen.engine.generation.IGenerationStrategy
import com.only4.codegen.engine.output.GenerationResult
import com.only4.codegen.engine.output.OutputType
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.toSnakeCase

class V2EnumStrategy : IGenerationStrategy<EnumV2Context> {
    override fun generate(context: EnumV2Context): List<GenerationResult> {
        val pkg = concatPackage(context.basePackage, "domain.aggregates", context.aggregate, "enums")
        val fileName = "${context.enumName}.kt"

        val constants = context.items.joinToString(",\n    ") { item ->
            val constName = (toSnakeCase(item.name) ?: item.name).uppercase()
            val descEscaped = item.desc.replace("\"", "\\\"")
            "$constName(${item.value}, \"$descEscaped\")"
        }

        val content = buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("enum class ${context.enumName}(val ${context.enumValueField}: Int, val ${context.enumNameField}: String) {")
            appendLine("    $constants;")
            appendLine()
            appendLine("    companion object {")
            appendLine("        fun fromValue(v: Int): ${context.enumName}? = entries.firstOrNull { it.${context.enumValueField} == v }")
            appendLine("    }")
            appendLine("}")
        }

        return listOf(
            GenerationResult(
                fileName = fileName,
                content = content,
                packageName = pkg,
                type = OutputType.ENUM,
            )
        )
    }
}

