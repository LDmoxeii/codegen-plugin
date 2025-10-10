package com.only.codegen.misc

import com.only.codegen.AbstractCodegenTask
import com.only.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_ID_ALIAS
import com.only.codegen.misc.SqlSchemaUtils.LEFT_QUOTES_4_LITERAL_STRING
import com.only.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_ID_ALIAS
import com.only.codegen.misc.SqlSchemaUtils.RIGHT_QUOTES_4_LITERAL_STRING
import com.only.codegen.misc.SqlSchemaUtils.executeQuery
import com.only.codegen.misc.SqlSchemaUtils.getType
import com.only.codegen.misc.SqlSchemaUtils.hasEnum
import com.only.codegen.misc.SqlSchemaUtils.hasType
import com.only.codegen.misc.SqlSchemaUtils.task


object SqlSchemaUtils4Mysql : SqlSchemaUtils.SqlSchemaDialect {

    private val ext get() = task!!.extension.get()
    private val db get() = ext.database
    private val gen get() = ext.generation

    // 构造 (table_name like ...) / not like 条件
    private fun buildTableNameCondition(patterns: String, positive: Boolean): String? =
        patterns.takeIf { it.isNotBlank() }
            ?.split(AbstractCodegenTask.PATTERN_SPLITTER.toRegex())
            ?.joinToString(" or ") {
                "table_name ${if (positive) "like" else "not like"} ${LEFT_QUOTES_4_LITERAL_STRING}$it$RIGHT_QUOTES_4_LITERAL_STRING"
            }
            ?.let { clause -> if (positive) "( $clause )" else "not ( $clause )" }

    override fun resolveTables(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val schema = db.schema.get()
        val conditions = mutableListOf(
            "table_schema = ${LEFT_QUOTES_4_LITERAL_STRING}$schema${RIGHT_QUOTES_4_LITERAL_STRING}"
        )
        buildTableNameCondition(db.tables.get(), true)?.let { conditions += it }
        buildTableNameCondition(db.ignoreTables.get(), false)?.let { conditions += it }

        val tableSql = buildString {
            appendLine("select * from ${LEFT_QUOTES_4_ID_ALIAS}information_schema${RIGHT_QUOTES_4_ID_ALIAS}.${LEFT_QUOTES_4_ID_ALIAS}tables${RIGHT_QUOTES_4_ID_ALIAS}")
            append("where ")
            append(conditions.joinToString(" and "))
        }
        return executeQuery(tableSql, connectionString, user, pwd)
    }

    override fun resolveColumns(connectionString: String, user: String, pwd: String): List<Map<String, Any?>> {
        val schema = db.schema.get()
        val conditions = mutableListOf(
            "table_schema = ${LEFT_QUOTES_4_LITERAL_STRING}$schema${RIGHT_QUOTES_4_LITERAL_STRING}"
        )
        buildTableNameCondition(db.tables.get(), true)?.let { conditions += it }
        buildTableNameCondition(db.ignoreTables.get(), false)?.let { conditions += it }

        val columnSql = buildString {
            appendLine("select * from ${LEFT_QUOTES_4_ID_ALIAS}information_schema${RIGHT_QUOTES_4_ID_ALIAS}.${LEFT_QUOTES_4_ID_ALIAS}columns${RIGHT_QUOTES_4_ID_ALIAS}")
            append("where ")
            append(conditions.joinToString(" and "))
        }
        return executeQuery(columnSql, connectionString, user, pwd)
    }

    private fun isBooleanTinyInt(name: String, columnType: String, comment: String): Boolean =
        ".deleted.".contains(".$name.") ||
                gen.deletedField.get().equals(name, ignoreCase = true) ||
                columnType.equals("tinyint(1)", ignoreCase = true) ||
                comment.contains("是否")

    override fun getColumnType(column: Map<String, Any?>): String {
        val dataType = column["DATA_TYPE"].toString().lowercase()
        val columnType = column["COLUMN_TYPE"].toString()
        val comment = SqlSchemaUtils.getComment(column)
        val columnName = SqlSchemaUtils.getColumnName(column).lowercase()

        // 自定义类型映射优先
        gen.typeRemapping.get()
            .takeIf { it.isNotEmpty() && it.containsKey(dataType) }
            ?.let { return it[dataType]!! }

        val datePkgJavaTime = gen.datePackage.get().equals("java.time", ignoreCase = true)
        val baseType = when (dataType) {
            "char", "varchar", "text", "mediumtext", "longtext" -> "String"
            "datetime", "timestamp" -> if (datePkgJavaTime) "java.time.LocalDateTime" else "java.util.Date"
            "date" -> if (datePkgJavaTime) "java.time.LocalDate" else "java.util.Date"
            "time" -> if (datePkgJavaTime) "java.time.LocalTime" else "java.util.Date"
            "int" -> "Int"
            "bigint" -> "Long"
            "smallint" -> "Short"
            "bit" -> "Boolean"
            "tinyint" -> if (isBooleanTinyInt(columnName, columnType, comment)) "Boolean" else "Byte"
            "float" -> "Float"
            "double" -> "Double"
            "decimal", "numeric" -> "java.math.BigDecimal"
            else -> throw IllegalArgumentException("Unsupported DATA_TYPE: ${column["DATA_TYPE"]}")
        }
        return if (isColumnNullable(column)) "$baseType?" else baseType
    }

    private fun parseBooleanDefault(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return when (value.trim().lowercase()) {
            "b'1'", "1", "true" -> "true"
            "b'0'", "0", "false" -> "false"
            else -> null
        }
    }

    override fun getColumnDefaultLiteral(column: Map<String, Any?>): String {
        val columnDefault = column["COLUMN_DEFAULT"] as String?
        val columnType = SqlSchemaUtils.getColumnType(column)

        if (hasType(column)) {
            val customType = getType(column)
            if (hasEnum(column) && task!!.enumPackageMap.containsKey(customType)) {
                val enumFqnPrefix = "${task!!.enumPackageMap[customType]}.$customType"
                return if (columnType.endsWith("?")) {
                    if (columnDefault.isNullOrEmpty()) "null"
                    else "$enumFqnPrefix.valueOf($columnDefault)"
                } else {
                    require(!columnDefault.isNullOrEmpty()) {
                        "Enum type $customType column ${SqlSchemaUtils.getColumnName(column)} can not have null default value"
                    }
                    "$enumFqnPrefix.valueOf($columnDefault)"
                }
            }
            return customType
        }

        return when (columnType) {
            "String" -> if (columnDefault.isNullOrEmpty()) """""""" else """"$columnDefault""""
            "String?" -> if (columnDefault.isNullOrEmpty()) "null" else """"$columnDefault""""
            "Int", "Short", "Byte", "Float", "Double" ->
                if (columnDefault.isNullOrEmpty()) "0" else columnDefault

            "Int?", "Short?", "Byte?", "Float?", "Double?" ->
                if (columnDefault.isNullOrEmpty()) "null" else columnDefault

            "Long" -> if (columnDefault.isNullOrEmpty()) "0L" else "${columnDefault}L"
            "Long?" -> if (columnDefault.isNullOrEmpty()) "null" else "${columnDefault}L"
            "Boolean" -> parseBooleanDefault(columnDefault) ?: "false"
            "Boolean?" -> columnDefault?.let { parseBooleanDefault(it) } ?: "null"
            "java.math.BigDecimal" ->
                if (columnDefault.isNullOrEmpty()) "java.math.BigDecimal.ZERO" else """java.math.BigDecimal("$columnDefault")"""

            "java.math.BigDecimal?" ->
                if (columnDefault.isNullOrEmpty()) "null" else """java.math.BigDecimal("$columnDefault")"""

            else -> "null"
        }
    }

    override fun isAutoUpdateDateColumn(column: Map<String, Any?>): Boolean =
        column["EXTRA"].toString().contains("on update CURRENT_TIMESTAMP")

    override fun isAutoInsertDateColumn(column: Map<String, Any?>): Boolean =
        column["COLUMN_DEFAULT"].toString().contains("CURRENT_TIMESTAMP")

    override fun isColumnInTable(column: Map<String, Any?>, table: Map<String, Any?>): Boolean =
        column["TABLE_NAME"].toString().equals(table["TABLE_NAME"].toString(), ignoreCase = true)

    override fun getOrdinalPosition(column: Map<String, Any?>): Int =
        (column["ORDINAL_POSITION"] as Number).toInt()

    override fun hasColumn(columnName: String, columns: List<Map<String, Any?>>): Boolean =
        columns.any { it["COLUMN_NAME"].toString().equals(columnName, ignoreCase = true) }

    override fun getName(tableOrColumn: Map<String, Any?>): String =
        if (tableOrColumn.containsKey("COLUMN_NAME")) getColumnName(tableOrColumn) else getTableName(tableOrColumn)

    override fun getColumnName(column: Map<String, Any?>): String = column["COLUMN_NAME"].toString()

    override fun getTableName(table: Map<String, Any?>): String = table["TABLE_NAME"].toString()

    override fun getColumnDbType(column: Map<String, Any?>): String = column["COLUMN_TYPE"].toString()

    override fun getColumnDbDataType(column: Map<String, Any?>): String = column["DATA_TYPE"].toString()

    override fun isColumnNullable(column: Map<String, Any?>): Boolean =
        column["IS_NULLABLE"].toString().equals("YES", ignoreCase = true)

    override fun isColumnPrimaryKey(column: Map<String, Any?>): Boolean =
        column["COLUMN_KEY"].toString().equals("PRI", ignoreCase = true)

    override fun getComment(tableOrColumn: Map<String, Any?>, cleanAnnotations: Boolean): String {
        var comment = when {
            tableOrColumn.containsKey("TABLE_COMMENT") -> tableOrColumn["TABLE_COMMENT"] as String? ?: ""
            tableOrColumn.containsKey("COLUMN_COMMENT") -> tableOrColumn["COLUMN_COMMENT"] as String? ?: ""
            else -> ""
        }
        if (cleanAnnotations) {
            comment = SqlSchemaUtils.ANNOTATION_PATTERN.matcher(comment).replaceAll("")
        }
        return comment.trim()
    }
}
