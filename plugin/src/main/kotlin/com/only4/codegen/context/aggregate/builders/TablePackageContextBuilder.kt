package com.only4.codegen.context.aggregate.builders

import com.only4.codegen.context.ContextBuilder
import com.only4.codegen.context.aggregate.MutableAggregateContext
import com.only4.codegen.misc.SqlSchemaUtils
import com.only4.codegen.misc.refPackage

class TablePackageContextBuilder: ContextBuilder<MutableAggregateContext> {
    override val order: Int = 40

    override fun build(context: MutableAggregateContext) {
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
