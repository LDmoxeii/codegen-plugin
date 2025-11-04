package com.only4.codegen.generators.aggregate.unique

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.generators.aggregate.AggregateTemplateGenerator
import com.only4.codegen.manager.QueryImportManager
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toLowerCamelCase
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class UniqueQueryGenerator : AggregateTemplateGenerator {
    override val tag: String = "query"
    override val order: Int = 22

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        val name = generatorName(table)
        return name.isNotBlank() && !ctx.typeMapping.containsKey(name)
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val resultContext = ctx.baseMap.toMutableMap()

        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val entityType = ctx.entityTypeMap[tableName]!!

        val selected = resolveSelectedConstraint(table)
        val deletedField = ctx.getString("deletedField")
        val allColumns = ctx.columnsMap[tableName]!!

        val requestParams = selected?.get("columns")
            .let { it as? List<Map<String, Any?>> ?: emptyList() }
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName ->
                val colMeta = allColumns.first { SqlSchemaUtils.getColumnName(it).equals(colName, ignoreCase = true) }
                val type = SqlSchemaUtils.getColumnType(colMeta)
                mapOf(
                    "name" to (toLowerCamelCase(colName) ?: colName),
                    "type" to type,
                    "isString" to (type.removeSuffix("?") == "String")
                )
            }

        val idColumn = allColumns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idTypeRaw = idColumn?.let { SqlSchemaUtils.getColumnType(it) } ?: "Long"
        val idType = idTypeRaw.removeSuffix("?")
        val excludeIdParamName = "exclude${entityType}Id"

        // imports
        val importManager = QueryImportManager(QueryImportManager.QueryType.SINGLE).apply { addBaseImports() }

        with(ctx) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Query", generatorName(table))
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "RequestParams", requestParams)
            resultContext.putContext(tag, "ExcludeIdParamName", excludeIdParamName)
            resultContext.putContext(tag, "IdType", idType)
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(aggregate)
        return "$basePackage${templatePackage}${`package`}${refPackage(generatorName(table))}"
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return ""
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val deletedField = ctx.getString("deletedField")

        constraints.forEach { cons ->
            val cols = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
            val filtered = cols.filter { c ->
                !c["columnName"].toString().equals(deletedField, ignoreCase = true)
            }
            if (filtered.isEmpty()) return@forEach
            val suffix = filtered.sortedBy { (it["ordinal"] as Number).toInt() }
                .joinToString("") { toUpperCamelCase(it["columnName"].toString()) ?: it["columnName"].toString() }
            val q = "Unique${entityType}${suffix}Qry"
            if (!ctx.typeMapping.containsKey(q)) {
                return q
            }
        }
        return ""
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueQueryGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/unique_query.kt.peb"
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
        val target = generatorName(table)
        val deletedField = ctx.getString("deletedField")
        return constraints.firstOrNull { cons ->
            val cols = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
            val filtered = cols.filter { c ->
                !c["columnName"].toString().equals(deletedField, ignoreCase = true)
            }
            if (filtered.isEmpty()) return@firstOrNull false
            val suffix = filtered.sortedBy { (it["ordinal"] as Number).toInt() }
                .joinToString("") { toUpperCamelCase(it["columnName"].toString()) ?: it["columnName"].toString() }
            val q = "Unique${entityType}${suffix}Qry"
            q == target
        }
    }
}
