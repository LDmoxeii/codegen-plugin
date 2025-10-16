package com.only4.codegen.generators.aggregate

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.template.TemplateNode

/**
 * 测试完整文件生成器
 *
 * 演示如何使用 segment 的上下文生成完整文件
 */
class TestFullFileGenerator : AggregateTemplateGenerator {
    override val tag = "testfull"  // 父 tag
    override val order = 20  // 在 TestFieldSegmentGenerator (order=15) 之后执行

    @Volatile
    private var generated = false

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        return !generated
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName] ?: "UnknownEntity"

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            // 文件级别的上下文
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "package", "test.package")
            resultContext.putContext(tag, "modulePath", context.domainPath)
            resultContext.putContext(tag, "templatePackage", "")
        }

        // 注意：fields 上下文会在 generateForTables 中通过 mergeSegmentContexts() 自动合并
        // 这里不需要手动获取

        return resultContext
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"  // 标记为 file，生成实际文件
                tag = this@TestFullFileGenerator.tag
                name = "Test{{ Entity }}.kt"
                format = "resource"
                data = "templates/test-full.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        generated = true
    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        return context.entityTypeMap[tableName] ?: "UnknownEntity"
    }

    override fun generatorFullName(table: Map<String, Any?>, context: AggregateContext): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName] ?: "UnknownEntity"
        return "test.package.Test$entityType"
    }
}
