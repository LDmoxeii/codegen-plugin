package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.manager.AggregateImportManager
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only.codegen.template.TemplateNode

/**
 * 聚合封装类生成器
 * 为聚合根生成聚合封装类，用于管理聚合根及其相关操作
 */
class AggregateGenerator : AggregateTemplateGenerator {
    override val tag = "aggregate"
    override val order = 40

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!context.getBoolean("generateAggregate", false)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        return !context.typeMapping.containsKey(generatorName(table, context))
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName]
        val aggregate = context.resolveAggregateWithModule(tableName)

        val entityType = context.entityTypeMap[tableName]!!
        val factoryType = "${entityType}Factory"
        val fullFactoryType = context.typeMapping[factoryType]!!

        val ids = columns!!.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        // 创建 ImportManager
        val importManager = AggregateImportManager()
        importManager.addBaseImports()
        importManager.add(fullFactoryType)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(refPackage(aggregate)))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "AggregateName", generatorName(table, context))

            resultContext.putContext(tag, "Factory", factoryType)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: AggregateContext
    ): String {
        return with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            "$basePackage${templatePackage}${`package`}${refPackage(aggregateType)}"

        }
    }


    override fun generatorName(
        table: Map<String, Any?>,
        context: AggregateContext
    ): String {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = entityTypeMap[tableName]!!

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            return aggregateType
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@AggregateGenerator.tag
                name = "{{ AggregateName }}.kt"
                format = "resource"
                data = "templates/aggregate.kt.peb"
                conflict = "skip"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
