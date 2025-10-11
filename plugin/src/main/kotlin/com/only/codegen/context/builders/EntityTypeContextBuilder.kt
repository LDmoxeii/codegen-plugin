package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.toUpperCamelCase

class EntityTypeContextBuilder: ContextBuilder {
    override val order: Int = 20

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            val tableName = SqlSchemaUtils.getTableName(table)

            val type = SqlSchemaUtils.getType(table).takeIf { it.isNotBlank() }
                ?: toUpperCamelCase(tableName)!!

            context.entityTypeMap[tableName] = type
        }
    }
}
