package com.only.codegen.generators

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.context.EntityContext
import com.only.codegen.misc.*
import com.only.codegen.template.TemplateNode

/**
 * Schema 文件生成器
 * 为每个实体生成对应的 Schema 类（类似 JPA Metamodel）
 */
class SchemaGenerator : TemplateGenerator {
    override val tag = "schema"
    override val order = 20

    private val generated = mutableSetOf<String>()

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!context.getBoolean("generateSchema", false)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = context.columnsMap[tableName] ?: return false
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }

        return ids.isNotEmpty() && !generated.contains(tableName)
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)
        val columns = context.columnsMap[tableName]!!

        val entityType = context.entityTypeMap[tableName]!!
        val fullEntityType = context.typeRemapping[entityType]!!

        // 准备列字段数据
        val fields = columns
            .filter { !SqlSchemaUtils.isIgnore(it) }
            .map { column ->
                val columnName = SqlSchemaUtils.getColumnName(column)
                val columnType = SqlSchemaUtils.getColumnType(column)
                val fieldName = toLowerCamelCase(columnName) ?: columnName
                val comment = SqlSchemaUtils.getComment(column)

                mapOf(
                    "fieldName" to fieldName,
                    "columnName" to columnName,
                    "fieldType" to columnType,
                    "comment" to comment
                )
            }

        // 准备关系字段数据
        val relationFields = mutableListOf<Map<String, Any?>>()
        context.relationsMap[tableName]?.forEach { (refTableName, relationInfo) ->
            val refInfos = relationInfo.split(";")
            if (refInfos[0] == "PLACEHOLDER") return@forEach

            val refEntityType = context.entityTypeMap[refTableName] ?: return@forEach
            val relation = refInfos[0].replace("*", "")
            val fieldName = when (relation) {
                "OneToMany", "ManyToMany" -> Inflector.pluralize(toLowerCamelCase(refEntityType) ?: refEntityType)
                else -> toLowerCamelCase(refEntityType) ?: refEntityType
            }

            relationFields.add(
                mapOf(
                    "fieldName" to fieldName,
                    "refEntityType" to refEntityType,
                    "relation" to relation
                )
            )
        }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(schemaPackage))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Schema", "S$entityType")
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "FullSchemaBaseType", typeRemapping["SchemaBase"]!!)
            resultContext.putContext(tag, "FullEntityType", fullEntityType)
            resultContext.putContext(tag, "fields", fields)
            resultContext.putContext(tag, "relationFields", relationFields)
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
            tag = this@SchemaGenerator.tag
            name = "{{ Schema }}.kt"
            format = "resource"
            data = "schema"
            conflict = "overwrite"
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

            val schemaType = "S$entityType"
            val fullSchemaType = "$basePackage${templatePackage}${`package`}${refPackage(schemaType)}"
            typeRemapping[entityType] = fullSchemaType

            generated.add(tableName)
        }
    }
}
