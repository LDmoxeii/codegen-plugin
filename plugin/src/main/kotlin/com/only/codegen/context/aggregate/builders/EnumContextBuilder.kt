package com.only.codegen.context.aggregate.builders

import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.aggregate.MutableAggregateContext
import com.only.codegen.misc.SqlSchemaUtils

/**
 * 枚举信息构建器
 */
class EnumContextBuilder : ContextBuilder<MutableAggregateContext> {
    override val order = 50

    override fun build(context: MutableAggregateContext) {
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName]!!

            columns.forEach { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumConfig = SqlSchemaUtils.getEnum(column)
                    if (enumConfig.isNotEmpty()) {
                        val enumType = SqlSchemaUtils.getType(column)

                        context.enumConfigMap[enumType] = enumConfig
                    }
                }
            }
        }
    }
}
