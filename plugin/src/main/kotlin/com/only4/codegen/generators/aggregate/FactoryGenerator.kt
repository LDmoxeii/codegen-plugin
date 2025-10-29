package com.only4.codegen.generators.aggregate

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.manager.FactoryImportManager
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.concatPackage
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

/**
 * Factory 文件生成器
 * 为聚合根生成工厂类
 */
class FactoryGenerator : AggregateTemplateGenerator {
    override val tag = "factory"
    override val order = 30

    companion object {
        private const val DEFAULT_FAC_PACKAGE = "factory"
    }

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        if (!(SqlSchemaUtils.hasFactory(table)) && ctx.getBoolean("generateAggregate")) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return false
        val columns = ctx.columnsMap[tableName] ?: return false
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }

        val factoryType = "${entityType}Factory"
        return ids.isNotEmpty() && !ctx.typeMapping.containsKey(factoryType)
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        val entityType = ctx.entityTypeMap[tableName]!!
        val fullEntityType = ctx.typeMapping[entityType]!!

        // 创建 ImportManager
        val importManager = FactoryImportManager()
        importManager.addBaseImports()
        importManager.add(fullEntityType)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(aggregate), refPackage(DEFAULT_FAC_PACKAGE))))

            resultContext.putContext(tag, "Factory", generatorName(table))
            resultContext.putContext(tag, "Payload", "${entityType}Payload")

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }


        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(
        table: Map<String, Any?>
    ): String {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            val factoryType = "${entityType}Factory"
            val fullFactoryType =
                "$basePackage${templatePackage}${`package`}${refPackage(DEFAULT_FAC_PACKAGE)}${refPackage(factoryType)}"
            return fullFactoryType
        }
    }

    context(ctx: AggregateContext)
    override fun generatorName(
        table: Map<String, Any?>
    ): String {
        return with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = entityTypeMap[tableName]!!

            "${entityType}Factory"
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@FactoryGenerator.tag
                name = "{{ Factory }}.kt"
                format = "resource"
                data = "templates/factory.kt.peb"
                conflict = "skip" // Factory 通常包含业务逻辑，不覆盖已有文件
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)

    }

}

