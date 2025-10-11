package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils

class ModuleContextBuilder:ContextBuilder {
    override val order: Int = 30

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)

            generateSequence(context.tableMap[tableName]) { currentTable ->
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
