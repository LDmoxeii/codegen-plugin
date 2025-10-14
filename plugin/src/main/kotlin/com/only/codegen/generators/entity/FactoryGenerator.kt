package com.only.codegen.generators.entity

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.entity.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * Factory 文件生成器
 * 为聚合根生成工厂类
 */
class FactoryGenerator : EntityTemplateGenerator {
    override val tag = "factory"
    override val order = 30

    companion object {
        private const val DEFAULT_FAC_PACKAGE = "factory"
    }

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        if (!(SqlSchemaUtils.hasFactory(table)) && context.getBoolean("generateAggregate")) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName] ?: return false
        val columns = context.columnsMap[tableName] ?: return false
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }

        val factoryType = "${entityType}Factory"
        return ids.isNotEmpty() && !context.typeMapping.containsKey(factoryType)
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)

        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityType = context.typeMapping[entityType]!!

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "DEFAULT_FAC_PACKAGE", DEFAULT_FAC_PACKAGE)
            resultContext.putContext(tag, "Factory", generatorName(table, context))
            resultContext.putContext(tag, "Payload", "${entityType}Payload")

            resultContext.putContext(tag, "fullEntityType", fullEntityType)
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
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
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag]!!)
            val `package` = refPackage(aggregate)

            val factoryType = "${entityType}Factory"
            val fullFactoryType =
                "$basePackage${templatePackage}${`package`}${refPackage(DEFAULT_FAC_PACKAGE)}${refPackage(factoryType)}"
            return fullFactoryType
        }
    }

    override fun generatorName(
        table: Map<String, Any?>,
        context: EntityContext
    ): String {
        return with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = entityTypeMap[tableName]!!

            "${entityType}Factory"
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@FactoryGenerator.tag
            name = "{{ DEFAULT_FAC_PACKAGE }}{{ SEPARATOR }}{{ Factory }}.kt"
            format = "resource"
            data = "templates/factory.kt.peb"
            conflict = "skip" // Factory 通常包含业务逻辑，不覆盖已有文件
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)

    }

}
