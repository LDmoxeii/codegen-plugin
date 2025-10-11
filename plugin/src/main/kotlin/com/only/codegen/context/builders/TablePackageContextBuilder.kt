package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.concatPackage

class TablePackageContextBuilder: ContextBuilder {
    override val order: Int = 30

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)
            val module = context.tableModuleMap[tableName]!!
            val aggregate = context.tableAggregateMap[tableName]!!
            context.tablePackageMap[tableName] = concatPackage(context.aggregatesPackage, module, aggregate.lowercase())
                .let { entityPackage ->
                "${context.getString("basePackage")}.$entityPackage"
            }
        }
    }
}
