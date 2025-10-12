package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils

/**
 * 表和列信息构建器
 */
class TableContextBuilder : EntityContextBuilder {
    override val order = 10

    override fun build(context: MutableEntityContext) {
        val tables = SqlSchemaUtils.resolveTables(
            context.getString("dbUrl"),
            context.getString("dbUsername"),
            context.getString("dbPassword"),
        )

        val allColumns = SqlSchemaUtils.resolveColumns(
            context.getString("dbUrl"),
            context.getString("dbUsername"),
            context.getString("dbPassword"),
        )

        tables.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = allColumns
                .filter { SqlSchemaUtils.isColumnInTable(it, table) }
                .sortedBy { SqlSchemaUtils.getOrdinalPosition(it) }

            context.tableMap[tableName] = table
            context.columnsMap[tableName] = columns
        }
    }
}
