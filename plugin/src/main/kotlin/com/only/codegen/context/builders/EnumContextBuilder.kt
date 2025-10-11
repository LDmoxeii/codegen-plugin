package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext
import com.only.codegen.misc.SqlSchemaUtils

/**
 * 枚举信息构建器
 * 扫描所有列的枚举配置，预填充 enumPackageMap
 */
class EnumContextBuilder : ContextBuilder {
    override val order = 50

    override fun build(context: MutableEntityContext) {
        context.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = context.columnsMap[tableName]!!

            columns.forEach { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumConfig = SqlSchemaUtils.getEnum(column)
                    if (enumConfig.isNotEmpty()) {
                        val enumType = SqlSchemaUtils.getType(column)

                        // 记录枚举配置
                        context.enumConfigMap[enumType] = enumConfig
                        context.enumTableNameMap[enumType] = tableName
                    }
                }
            }
        }
    }
}
