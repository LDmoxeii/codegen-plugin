package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.manager.SchemaImportManager
import com.only.codegen.misc.*
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only.codegen.template.TemplateNode

/**
 * Schema 文件生成器
 * 为每个实体生成对应的 Schema 类（类似 JPA Metamodel）
 */
class SchemaGenerator : AggregateTemplateGenerator {
    override val tag = "schema"
    override val order = 50

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!context.getBoolean("generateSchema", false)) return false

        return !context.typeMapping.containsKey(generatorName(table, context))
    }

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = context.resolveAggregateWithModule(tableName)
        val columns = context.columnsMap[tableName]!!

        val aggregateTypeTemplate = context.getString("aggregateTypeTemplate")

        val entityType = context.entityTypeMap[tableName]!!
        val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

        val isAggregateRoot = SqlSchemaUtils.isAggregateRoot(table)
        val generateAggregate = context.getBoolean("generateAggregate")
        val repositorySupportQuerydsl = context.getBoolean("repositorySupportQuerydsl")

        // 创建 ImportManager
        val importManager = SchemaImportManager()
        importManager.addBaseImports()
        importManager.add(
            context.typeMapping[entityType]!!,
            context.typeMapping["Schema"]!!,
        )
        importManager.addIfNeeded(
            isAggregateRoot && repositorySupportQuerydsl,
            "com.only4.cap4k.ddd.domain.repo.JpaPredicate",
            "com.querydsl.core.types.OrderSpecifier",
            "com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate",
            "com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicate"
        )
        importManager.addIfNeeded(isAggregateRoot && repositorySupportQuerydsl) { context.typeMapping["Q$entityType"]!! }
        importManager.addIfNeeded(isAggregateRoot && repositorySupportQuerydsl) { context.typeMapping[aggregateType]!! }

        // 准备列字段数据
        val fields = columns
            .filter { !SqlSchemaUtils.isIgnore(it) }
            .map { column ->
                val columnName = SqlSchemaUtils.getColumnName(column)
                val columnType = SqlSchemaUtils.getColumnType(column)
                val fieldName = toLowerCamelCase(columnName) ?: columnName
                val comment = SqlSchemaUtils.getComment(column)

                if (SqlSchemaUtils.hasEnum(column)) {
                    if (context.typeMapping.containsKey(columnType)) {
                        importManager.add(context.typeMapping[columnType]!!)
                    }
                }

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
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Schema", "S$entityType")

            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "EntityVar", toLowerCamelCase(entityType) ?: entityType)
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "SchemaBase", "Schema")
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "fields", fields)
            resultContext.putContext(tag, "relationFields", relationFields)

            resultContext.putContext(tag, "isAggregateRoot", isAggregateRoot)
            resultContext.putContext(tag, "generateAggregate", generateAggregate)

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

            val schemaType = "S$entityType"
            return "$basePackage${templatePackage}${`package`}${refPackage(schemaType)}"
        }
    }

    override fun generatorName(table: Map<String, Any?>, context: AggregateContext): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]!!
        return "S$entityType"
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SchemaGenerator.tag
                name = "{{ Schema }}.kt"
                format = "resource"
                data = "templates/schema.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
