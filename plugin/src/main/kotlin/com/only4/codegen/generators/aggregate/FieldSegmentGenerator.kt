package com.only4.codegen.generators.aggregate

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.template.TemplateNode

/**
 * Entity 字段片段生成器
 *
 * 负责构建 entity 的字段上下文（columns）
 * 不生成实际文件，只缓存上下文供 EntityGenerator 使用
 */
class FieldSegmentGenerator : AggregateTemplateGenerator {
    override val tag = "entity:field"  // 复合 tag: parentTag:segmentVar
    override val order = 15  // 在 EntityGenerator (order=20) 之前执行

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        return context.columnsMap.containsKey(tableName)
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!

        // 简化示例：直接使用列数据
        // 实际应用中，这里应该调用 EntityGenerator.prepareColumnData()
        val columnDataList = columns.map { column ->
            mapOf(
                "columnName" to SqlSchemaUtils.getColumnName(column),
                "fieldName" to SqlSchemaUtils.getColumnName(column).lowercase(),
                "fieldType" to SqlSchemaUtils.getColumnType(column),
                "comment" to SqlSchemaUtils.getComment(column),
                "needGenerate" to true
            )
        }

        return mapOf("columns" to columnDataList)
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "segment"  // 关键：标记为 segment
                tag = this@FieldSegmentGenerator.tag
                format = "resource"
                data = "templates/segments/field.kt.peb"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        // Segment generator: 缓存上下文到 segmentContextCache
        val tableName = SqlSchemaUtils.getTableName(table)
        val parentTag = tag.substringBefore(":")
        val segmentVar = tag.substringAfter(":")
        val cacheKey = "$parentTag:$tableName:$segmentVar"

        // 获取已缓存的上下文（来自 cachedContext）
        val templateNode = getDefaultTemplateNodes().first()
        val segmentContext = templateNode.cachedContext ?: buildContext(table, context)

        context.putSegmentContext(cacheKey, segmentContext)
    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        return "field-segment"
    }

    override fun generatorFullName(table: Map<String, Any?>, context: AggregateContext): String {
        return "field-segment"  // Segment 不需要完整名称
    }
}
