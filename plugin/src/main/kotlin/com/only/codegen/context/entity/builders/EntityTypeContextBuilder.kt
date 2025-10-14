package com.only.codegen.context.entity.builders

import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.entity.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.toUpperCamelCase

class EntityTypeContextBuilder: ContextBuilder<MutableEntityContext> {
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
