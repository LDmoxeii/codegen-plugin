package com.only4.codegen.generators.aggregate.unique

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.generators.aggregate.AggregateTemplateGenerator
import com.only4.codegen.manager.ValidatorImportManager
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toLowerCamelCase
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class UniqueValidatorGenerator : AggregateTemplateGenerator {
    override val tag: String = "validator"
    override val order: Int = 28

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        val name = generatorName(table)
        if (name.isBlank() || ctx.typeMapping.containsKey(name)) return false

        // 依赖 Query 已生成
        val queryName = UniqueQueryGenerator().generatorName(table)
        return queryName.isNotBlank() && ctx.typeMapping.containsKey(queryName)
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val resultContext = ctx.baseMap.toMutableMap()

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName]!!

        val selected = resolveSelectedConstraint(table)
        val deletedField = ctx.getString("deletedField")
        val allColumns = ctx.columnsMap[tableName]!!

        val requestProps = selected?.get("columns")
            .let { it as? List<Map<String, Any?>> ?: emptyList() }
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName ->
                val colMeta = allColumns.first { SqlSchemaUtils.getColumnName(it).equals(colName, ignoreCase = true) }
                val type = SqlSchemaUtils.getColumnType(colMeta)
                mapOf(
                    "name" to (toLowerCamelCase(colName) ?: colName),
                    "type" to type,
                    "isString" to (type.removeSuffix("?") == "String"),
                    "paramProperty" to "\"${toLowerCamelCase(colName) ?: colName}Field\""
                )
            }

        val idColumn = allColumns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idTypeRaw = idColumn?.let { SqlSchemaUtils.getColumnType(it) } ?: "Long"
        val idType = if (idTypeRaw.endsWith("?")) idTypeRaw.removeSuffix("?") else idTypeRaw
        val entityIdPropDefault = (toLowerCamelCase(SqlSchemaUtils.getColumnName(idColumn ?: emptyMap())) ?: "${toLowerCamelCase(entityType) ?: entityType}Id")
        val entityIdParam = "${toLowerCamelCase(entityType) ?: entityType}IdField"

        // imports
        val importManager = ValidatorImportManager().apply { addBaseImports() }
        importManager.add(
            "com.only4.cap4k.ddd.core.Mediator",
            // kotlin reflect
            "kotlin.reflect.full.memberProperties",
            // unique query type
            ctx.typeMapping[UniqueQueryGenerator().generatorName(table)]!!
        )

        with(ctx) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(""))

            resultContext.putContext(tag, "Validator", generatorName(table))
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "FieldParams", requestProps.map {
                mapOf(
                    "param" to "${it["name"]}Field",
                    "default" to it["name"]
                )
            })
            resultContext.putContext(tag, "RequestProps", requestProps)
            resultContext.putContext(tag, "EntityIdParam", entityIdParam)
            resultContext.putContext(tag, "EntityIdDefault", entityIdPropDefault)
            resultContext.putContext(tag, "IdType", idType)
            resultContext.putContext(tag, "ExcludeIdParamName", "exclude${entityType}Id")
            resultContext.putContext(tag, "Query", UniqueQueryGenerator().generatorName(table))
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage("")
        return "$basePackage${templatePackage}${`package`}${refPackage(generatorName(table))}"
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return ""
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        constraints.forEach { cons ->
            val cols = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
            val suffix = cols.sortedBy { (it["ordinal"] as Number).toInt() }
                .joinToString("") { toUpperCamelCase(it["columnName"].toString()) ?: it["columnName"].toString() }
            val name = "Unique${entityType}${suffix}"
            if (!ctx.typeMapping.containsKey(name)) return name
        }
        return ""
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueValidatorGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ Validator }}.kt"
                format = "resource"
                data = "templates/unique_validator.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }

    context(ctx: AggregateContext)
    private fun resolveSelectedConstraint(table: Map<String, Any?>): Map<String, Any?>? {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return null
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val targetName = generatorName(table)
        return constraints.firstOrNull { cons ->
            val cols = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
            val suffix = cols.sortedBy { (it["ordinal"] as Number).toInt() }
                .joinToString("") { toUpperCamelCase(it["columnName"].toString()) ?: it["columnName"].toString() }
            val name = "Unique${entityType}${suffix}"
            name == targetName
        }
    }
}

