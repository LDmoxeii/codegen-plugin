package com.only.codegen.generators.entity

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
class AggregateGenerator : EntityTemplateGenerator {
    override val tag = "aggregate"
    override val order = 40

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!context.getBoolean("generateAggregate", false)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        return !context.typeMapping.containsKey(generatorName(table, context))
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

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(context.aggregatesPackage))
            resultContext.putContext(tag, "package", refPackage(refPackage(aggregate)))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "AggregateName", generatorName(table, context))

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

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        return with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = templatePackage[tag]!!
            val `package` = refPackage(aggregate)

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            "$basePackage${templatePackage}${`package`}${refPackage(aggregateType)}"

        }
    }


    override fun generatorName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = entityTypeMap[tableName]!!

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            return aggregateType
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@AggregateGenerator.tag
            name = "{{ AggregateName }}.kt"
            format = "resource"
            data = "templates/aggregate.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
       context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
