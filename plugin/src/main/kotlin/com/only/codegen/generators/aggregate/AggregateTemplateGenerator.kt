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

    /**
     * 获取默认模板节点列表
     *
     * 一个表可能需要生成多个文件，例如：
     * - Entity 可能需要生成 Entity.kt + EntityRepository.kt + EntityMapper.kt
     * - Aggregate 可能需要生成 Aggregate.kt + AggregateService.kt
     *
     * 使用方可以通过 TemplateNode.pattern 属性来过滤需要生成的模板
     *
     * @return 模板节点列表
     */
    fun getDefaultTemplateNodes(): List<TemplateNode>

    fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {}
}
