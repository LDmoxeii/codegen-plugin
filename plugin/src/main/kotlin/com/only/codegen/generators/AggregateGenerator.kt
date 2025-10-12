package com.only.codegen.generators

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.entity.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only.codegen.template.TemplateNode

/**
 * 聚合封装类生成器
 * 为聚合根生成聚合封装类，用于管理聚合根及其相关操作
 */
class AggregateGenerator : TemplateGenerator {
    override val tag = "aggregate"
    override val order = 40

    private val generated = mutableSetOf<String>()

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!context.getBoolean("generateAggregate", false)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName] ?: return false
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }

        return ids.isNotEmpty() && !generated.contains(tableName)
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]
        val aggregate = context.resolveAggregateWithModule(tableName)

        val entityType = context.entityTypeMap[tableName]!!
        val fullFactoryType = context.typeMapping["${entityType}Factory"]!!

        val ids = columns!!.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        val resultContext = context.baseMap.toMutableMap()

        // 获取聚合名称模板
        val aggregateTypeTemplate = context.getString("aggregateTypeTemplate")

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(context.aggregatesPackage))
            resultContext.putContext(tag, "package", refPackage(refPackage(aggregate)))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "AggregateName", renderString(aggregateTypeTemplate, resultContext))

            resultContext.putContext(tag, "fullFactoryType", fullFactoryType)
            resultContext.putContext(tag, "Factory", "${entityType}Factory")
        }

        // 准备注释行
        val commentLines = SqlSchemaUtils.getComment(table)
            .split(Regex(AbstractCodegenTask.PATTERN_LINE_BREAK))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                if (line.endsWith(";")) line.dropLast(1).trim() else line
            }
            .filter { it.isNotEmpty() }

        with(context) {
            resultContext.putContext(tag, "commentLines", commentLines)
        }

        return resultContext
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@AggregateGenerator.tag
            name = "{{ AggregateName }}.kt"
            format = "resource"
            data = "aggregate"
            conflict = "skip"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(aggregatesPackage)
            val `package` = refPackage(aggregate)

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            val fullAggregateType = "$basePackage${templatePackage}${`package`}${refPackage(aggregateType)}"
            typeMapping[aggregateType] = fullAggregateType

            generated.add(tableName)
        }
    }
}
