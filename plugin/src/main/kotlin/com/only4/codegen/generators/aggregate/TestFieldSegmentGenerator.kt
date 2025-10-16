package com.only4.codegen.generators.aggregate

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.template.TemplateNode

/**
 * 测试字段片段生成器
 *
 * 演示如何创建一个 segment generator
 */
class TestFieldSegmentGenerator : AggregateTemplateGenerator {
    override val tag = "testfull:field"  // 复合 tag: parentTag:segmentVar
    override val order = 15  // 在 TestFullFileGenerator (order=20) 之前执行

    @Volatile
    private var generated = false

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        return !generated
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]!!

        // 构建字段数据列表
        val fieldDataList = columns.map { column ->
            mapOf(
                "columnName" to "testColumnName",
                "fieldName" to "testFieldName",
                "fieldType" to "testFieldType",
                "comment" to "testComment",
                "needGenerate" to true
            )
        }

        // 缓存到 segmentContextCache
        val parentTag = tag.substringBefore(":")
        val segmentVar = tag.substringAfter(":")
        val cacheKey = "$parentTag:$tableName:$segmentVar"

        context.putSegmentContext(cacheKey, mapOf("fields" to fieldDataList))

        return mapOf("fields" to fieldDataList)
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "segment"  // 标记为 segment，不生成文件
                tag = this@TestFieldSegmentGenerator.tag
                format = "resource"
                data = "templates/segments/test-field.kt.peb"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        generated = true

    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        return "test-field-segment"
    }

    override fun generatorFullName(table: Map<String, Any?>, context: AggregateContext): String {
        return "test-field-segment"
    }
}
