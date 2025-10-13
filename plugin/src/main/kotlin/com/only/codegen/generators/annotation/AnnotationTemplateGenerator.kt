package com.only.codegen.generators.annotation

import com.only.codegen.context.annotation.AggregateInfo
import com.only.codegen.context.annotation.AnnotationContext
import com.only.codegen.template.TemplateNode

interface AnnotationTemplateGenerator {

    val tag: String

    val order: Int

    fun shouldGenerate(aggregateInfo: AggregateInfo, context: AnnotationContext): Boolean

    fun buildContext(aggregateInfo: AggregateInfo, context: AnnotationContext): Map<String, Any?>

    fun getDefaultTemplateNode(): TemplateNode

    fun onGenerated(aggregateInfo: AggregateInfo, context: AnnotationContext) {}
}
