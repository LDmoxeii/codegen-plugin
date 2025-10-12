package com.only.codegen.context.entity

import com.only.codegen.misc.SqlSchemaUtils

class ModuleContextBuilder:EntityContextBuilder {
    override val order: Int = 20

    override fun build(context: MutableEntityContext) {
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
