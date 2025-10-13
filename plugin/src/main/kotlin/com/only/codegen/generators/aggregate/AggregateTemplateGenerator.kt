package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateInfo
import com.only.codegen.context.aggregate.AnnotationContext
import com.only.codegen.template.TemplateNode

interface AggregateTemplateGenerator {

    val tag: String

    val order: Int

    fun shouldGenerate(aggregateInfo: AggregateInfo, context: AnnotationContext): Boolean

    fun buildContext(aggregateInfo: AggregateInfo, context: AnnotationContext): Map<String, Any?>

    fun getDefaultTemplateNode(): TemplateNode

    fun onGenerated(aggregateInfo: AggregateInfo, context: AnnotationContext) {}
}
