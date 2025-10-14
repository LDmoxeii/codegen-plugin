package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.template.TemplateNode

interface AggregateTemplateGenerator {
    val tag: String

    val order: Int

    fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean

    fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?>

    fun generatorFullName(table: Map<String, Any?>, context: AggregateContext): String

    fun generatorName(table: Map<String, Any?>, context: AggregateContext): String

    fun getDefaultTemplateNode(): TemplateNode

    fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {}
}
