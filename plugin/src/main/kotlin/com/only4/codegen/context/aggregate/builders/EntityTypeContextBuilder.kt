package com.only4.codegen.context.aggregate.builders

import com.only4.codegen.context.ContextBuilder
import com.only4.codegen.context.aggregate.MutableAggregateContext
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.toUpperCamelCase

class EntityTypeContextBuilder: ContextBuilder<MutableAggregateContext> {
    override val order: Int = 20

    override fun build(context: MutableAggregateContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)

            val type = SqlSchemaUtils.getType(table).takeIf { it.isNotBlank() }
                ?: toUpperCamelCase(tableName)!!

            context.entityTypeMap[tableName] = type
        }
    }
}
