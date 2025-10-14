package com.only.codegen.context.entity.builders

import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.entity.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.toSnakeCase

class AggregateContextBuilder: ContextBuilder<MutableEntityContext> {
    override val order: Int = 30

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            var aggregateRootTableName = tableName

            var result = ""

            generateSequence(table) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else {
                    context.tableMap[parent]?.also {
                        aggregateRootTableName = SqlSchemaUtils.getTableName(it)
                    }
                }
            }.forEach { table ->
                val aggregate = SqlSchemaUtils.getAggregate(table)

                if (SqlSchemaUtils.isAggregateRoot(table) || aggregate.isNotBlank()) {
                    result = aggregate.takeIf { it.isNotBlank() }
                        ?: (toSnakeCase(context.entityTypeMap[aggregateRootTableName]) ?: "")
                    return@forEach
                }
            }

            if (result.isBlank()) {
                result = toSnakeCase(context.entityTypeMap[aggregateRootTableName]) ?: ""
            }

            context.tableAggregateMap[tableName] = result
        }
    }
}
