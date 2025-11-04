package com.only4.codegen.generators.aggregate.unique

import com.only4.codegen.context.aggregate.AggregateContext
import com.only4.codegen.generators.aggregate.AggregateTemplateGenerator
import com.only4.codegen.manager.QueryHandlerImportManager
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toLowerCamelCase
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class UniqueQueryHandlerGenerator : AggregateTemplateGenerator {
    override val tag: String = "query_handler"
    override val order: Int = 24

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        // 仅依据当前生成名是否可用（生成名内部已确保前置条件满足）
        val handlerName = generatorName(table)
        return handlerName.isNotBlank()
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

        val whereProps = selected?.get("columns")
            .let { it as? List<Map<String, Any?>> ?: emptyList() }
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName -> toLowerCamelCase(colName) ?: colName }

        val idColumn = allColumns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idPropName = idColumn?.let { toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) } ?: "id"
        val excludeIdParamName = "exclude${entityType}Id"

        // imports
        val importManager = QueryHandlerImportManager(QueryHandlerImportManager.QueryType.SINGLE).apply {
            addBaseImports()
            add(ctx.typeMapping[getQueryName(table)]!!)
        }

        // Jimmer & SQL client
        importManager.add(
            "org.babyfish.jimmer.sql.kt.KSqlClient",
            "org.babyfish.jimmer.sql.kt.ast.expression.eq",
            "org.babyfish.jimmer.sql.kt.ast.expression.`ne?`",
            "org.babyfish.jimmer.sql.kt.exists",
        )

        // share model imports
        val basePackage = ctx.getString("basePackage")
        val shareModelPkg = basePackage + refPackage(ctx.templatePackage["query"] ?: "") + "._share.model"
        importManager.add("$shareModelPkg.$entityType")
        whereProps.forEach { prop -> importManager.add("$shareModelPkg.$prop") }
        importManager.add("$shareModelPkg.$idPropName")

        with(ctx) {
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "QueryHandler", generatorName(table))
            resultContext.putContext(tag, "Query", getQueryName(table))
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "WhereProps", whereProps)
            resultContext.putContext(tag, "IdPropName", idPropName)
            resultContext.putContext(tag, "ExcludeIdParamName", excludeIdParamName)
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
    private fun getQueryName(table: Map<String, Any?>): String {
        val handlerName = generatorName(table)
        return handlerName.removeSuffix("Handler")
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
            val h = toUpperCamelCase("${q}Handler")!!
            if (ctx.typeMapping.containsKey(q) && !ctx.typeMapping.containsKey(h)) return h
        }
        return ""
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueQueryHandlerGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/unique_query_handler.kt.peb"
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
        val deletedField = ctx.getString("deletedField")
        val targetHandler = generatorName(table)
        return constraints.firstOrNull { cons ->
            val cols = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
            val filtered = cols.filter { c ->
                !c["columnName"].toString().equals(deletedField, ignoreCase = true)
            }
            if (filtered.isEmpty()) return@firstOrNull false
            val suffix = filtered.sortedBy { (it["ordinal"] as Number).toInt() }
                .joinToString("") { toUpperCamelCase(it["columnName"].toString()) ?: it["columnName"].toString() }
            val q = "Unique${entityType}${suffix}Qry"
            val h = toUpperCamelCase("${q}Handler")!!
            h == targetHandler
        }
    }
}
