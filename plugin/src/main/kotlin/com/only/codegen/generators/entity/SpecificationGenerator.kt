package com.only.codegen.generators.entity

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.entity.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * Specification 文件生成器
 * 为每个实体生成规约（Specification）基类
 */
class SpecificationGenerator : EntityTemplateGenerator {
    override val tag = "specification"
    override val order = 30

    companion object {
        private const val DEFAULT_SPEC_PACKAGE = "specs"
    }

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
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

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)
        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityType = context.typeMapping[entityType]!!

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(context.aggregatesPackage))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "DEFAULT_SPEC_PACKAGE", DEFAULT_SPEC_PACKAGE)
            resultContext.putContext(tag, "Specification", "${entityType}Specification")

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "fullEntityType", fullEntityType)
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

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@SpecificationGenerator.tag
            name = "{{ DEFAULT_SPEC_PACKAGE }}{{ SEPARATOR }}{{ Specification }}.kt"
            format = "resource"
            data = "templates/specification.peb"
            conflict = "skip" // Specification 通常包含业务逻辑，不覆盖已有文件
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

            val specificationType = "${entityType}Specification"
            val fullSpecificationType = "$basePackage${templatePackage}${`package`}${refPackage(DEFAULT_SPEC_PACKAGE)}${
                refPackage(specificationType)
            }"
            typeMapping[specificationType] = fullSpecificationType
        }
    }
}
