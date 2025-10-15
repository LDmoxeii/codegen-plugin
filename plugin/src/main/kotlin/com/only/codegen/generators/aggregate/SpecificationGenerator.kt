package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * Specification 文件生成器
 * 为每个实体生成规约（Specification）基类
 */
class SpecificationGenerator : AggregateTemplateGenerator {
    override val tag = "specification"
    override val order = 30

    companion object {
        private const val DEFAULT_SPEC_PACKAGE = "specs"
    }

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        if (!(SqlSchemaUtils.hasSpecification(table)) && context.getBoolean("generateAggregate")) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName] ?: return false
        val columns = context.columnsMap[tableName] ?: return false
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }

        val specificationType = "${entityType}Specification"
        return ids.isNotEmpty() && !context.typeMapping.containsKey(specificationType)
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)
        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityType = context.typeMapping[entityType]!!

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(context.templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "DEFAULT_SPEC_PACKAGE", DEFAULT_SPEC_PACKAGE)
            resultContext.putContext(tag, "Specification", generatorName(table, context))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "fullEntityType", fullEntityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
        }


        return resultContext
    }

    override fun generatorFullName(table: Map<String, Any?>, context: AggregateContext): String {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag]!!)
            val `package` = refPackage(aggregate)

            val specificationType = "${entityType}Specification"
            return "$basePackage${templatePackage}${`package`}${refPackage(DEFAULT_SPEC_PACKAGE)}${
                refPackage(
                    specificationType
                )
            }"
        }
    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        return "${entityType}Specification"
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SpecificationGenerator.tag
                name = "{{ DEFAULT_SPEC_PACKAGE }}{{ SEPARATOR }}{{ Specification }}.kt"
                format = "resource"
                data = "templates/specification.kt.peb"
                conflict = "skip" // Specification 通常包含业务逻辑，不覆盖已有文件
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
