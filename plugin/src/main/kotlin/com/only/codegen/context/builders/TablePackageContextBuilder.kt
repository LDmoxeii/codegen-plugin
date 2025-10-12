package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage

class TablePackageContextBuilder: ContextBuilder {
    override val order: Int = 40

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            with(context) {
                val tableName = SqlSchemaUtils.getTableName(table)
                val aggregate = resolveAggregateWithModule(tableName)
                context.tablePackageMap[tableName] = "${getString("basePackage")}${refPackage(aggregatesPackage)}${refPackage(aggregate)}"
            }
        }
    }
}
