package com.only.codegen.context.aggregate.builders

import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.aggregate.MutableAggregateContext
import com.only.codegen.misc.SqlSchemaUtils

class ModuleContextBuilder: ContextBuilder<MutableAggregateContext> {
    override val order: Int = 20

    override fun build(context: MutableAggregateContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)

            generateSequence(table) { currentTable ->
                val parent = SqlSchemaUtils.getParent(currentTable)
                if (parent.isBlank()) null else context.tableMap[parent]
            }.forEach { table ->
                val module = SqlSchemaUtils.getModule(table)

                if (SqlSchemaUtils.isAggregateRoot(table) || module.isNotBlank()) {
                    context.tableModuleMap[tableName] = module
                    return@forEach
                }
            }
        }
    }
}
