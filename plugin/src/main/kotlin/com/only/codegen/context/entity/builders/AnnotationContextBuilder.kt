package com.only.codegen.context.entity.builders

import com.only.codegen.context.ContextBuilder
import com.only.codegen.context.entity.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils.getAnnotations
import com.only.codegen.misc.SqlSchemaUtils.getComment
import com.only.codegen.misc.SqlSchemaUtils.getTableName

class AnnotationContextBuilder: ContextBuilder<MutableEntityContext> {
    override val order: Int = 20

    override fun build(context: MutableEntityContext) {
        with(context) {
            tableMap.values.forEach { table ->
                val tableName = getTableName(table)
                val tableComment = getComment(table)

                annotationsMap[tableComment] = getAnnotations(table)

                columnsMap[tableName]!!.forEach { column ->
                    val columnComment = getComment(column)

                    annotationsMap[columnComment] = getAnnotations(column)
                }
            }
        }
    }
}
