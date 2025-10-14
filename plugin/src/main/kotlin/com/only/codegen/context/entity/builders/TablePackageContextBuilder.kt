package com.only.codegen.context.entity.builders

import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.entity.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage

class TablePackageContextBuilder: ContextBuilder<MutableEntityContext> {
    override val order: Int = 40

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            with(context) {
                val tableName = SqlSchemaUtils.getTableName(table)
                val aggregate = resolveAggregateWithModule(tableName)
                tablePackageMap[tableName] =
                    "${getString("basePackage")}${refPackage(templatePackage["entity"]!!)}${refPackage(aggregate)}"
            }
        }
    }
}
