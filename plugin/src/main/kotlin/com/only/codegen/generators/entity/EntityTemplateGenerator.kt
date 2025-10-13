package com.only.codegen.generators.entity

import com.only.codegen.context.entity.EntityContext
import com.only.codegen.template.TemplateNode

interface EntityTemplateGenerator {
    val tag: String

    val order: Int

    fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean

    fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?>

    fun generatorFullName(table: Map<String, Any?>, context: EntityContext): String

    fun generatorName(table: Map<String, Any?>, context: EntityContext): String

    fun getDefaultTemplateNode(): TemplateNode

    fun onGenerated(table: Map<String, Any?>, context: EntityContext) {}
}
