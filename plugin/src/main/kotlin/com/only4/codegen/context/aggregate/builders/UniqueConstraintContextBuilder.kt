package com.only4.codegen.context.aggregate.builders

import com.only4.codegen.context.ContextBuilder
import com.only4.codegen.context.aggregate.MutableAggregateContext
import com.only4.codegen.misc.SqlSchemaUtils

/**
 * 唯一约束信息构建器
 */
class UniqueConstraintContextBuilder : ContextBuilder<MutableAggregateContext> {
    override val order: Int = 25

    override fun build(context: MutableAggregateContext) {
        val dbUrl = context.getString("dbUrl")
        val username = context.getString("dbUsername")
        val password = context.getString("dbPassword")
        val schema = context.getString("dbSchema")

        // 查询当前 schema 下所有 UNIQUE 约束
        val L = SqlSchemaUtils.LEFT_QUOTES_4_LITERAL_STRING
        val R = SqlSchemaUtils.RIGHT_QUOTES_4_LITERAL_STRING

        val sql = """
            select 
                tc.table_name as TABLE_NAME,
                tc.constraint_name as CONSTRAINT_NAME,
                kcu.column_name as COLUMN_NAME,
                kcu.ordinal_position as ORDINAL_POSITION
            from information_schema.table_constraints tc
            join information_schema.key_column_usage kcu
              on tc.table_schema = kcu.table_schema 
             and tc.table_name = kcu.table_name 
             and tc.constraint_name = kcu.constraint_name
            where tc.table_schema = $L$schema$R 
              and tc.constraint_type = 'UNIQUE'
            order by tc.table_name, tc.constraint_name, kcu.ordinal_position
        """.trimIndent()

        val rows = SqlSchemaUtils.executeQuery(sql, dbUrl, username, password)

        val byTable = rows.groupBy { it["TABLE_NAME"].toString() }
        byTable.forEach { (tableName, items) ->
            // 仅处理已选择的表
            if (!context.tableMap.containsKey(tableName)) return@forEach

            val byConstraint = items.groupBy { it["CONSTRAINT_NAME"].toString() }
            val constraints = byConstraint.map { (cName, cols) ->
                val columnList = cols.sortedBy { (it["ORDINAL_POSITION"] as Number).toInt() }
                    .map { col ->
                        mapOf(
                            "columnName" to col["COLUMN_NAME"].toString(),
                            "ordinal" to (col["ORDINAL_POSITION"] as Number).toInt()
                        )
                    }
                mapOf(
                    "constraintName" to cName,
                    "columns" to columnList
                )
            }

            context.uniqueConstraintsMap[tableName] = constraints
        }
    }
}

