package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils

class EntityTypeContextBuilder: ContextBuilder {
    override val order: Int = 20

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)

            val type = SqlSchemaUtils.getType(table)
            context.entityTypeMap[tableName] = type
        }
    }
}
