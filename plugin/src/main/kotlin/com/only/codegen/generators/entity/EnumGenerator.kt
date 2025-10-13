package com.only.codegen.generators.entity

import com.only.codegen.context.entity.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

/**
 * 枚举文件生成器
 */
class EnumGenerator : EntityTemplateGenerator {
    override val tag = "enum"
    override val order = 10

    @Volatile
    private lateinit var currentEnumType: String

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        with(context) {
            if (SqlSchemaUtils.isIgnore(table)) return false
            if (SqlSchemaUtils.hasRelation(table)) return false

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName] ?: return false

            // 检查是否有未生成的枚举列
            return columns.any { column ->
                if (!SqlSchemaUtils.hasEnum(column) || SqlSchemaUtils.isIgnore(column)) {
                    false
                } else {
                    val enumType = SqlSchemaUtils.getType(column)
                    !typeMapping.containsKey(enumType)
                }
            }
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        with(context) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName]!!
            val aggregate = resolveAggregateWithModule(tableName)

            columns.first { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumType = SqlSchemaUtils.getType(column)
                    if (!typeMapping.containsKey(enumType)) {
                        currentEnumType = enumType
                        true
                    } else false
                } else false
            }

            val enumConfig = enumConfigMap[currentEnumType]!!

            val enumItems = enumConfig.toSortedMap().map { (value, arr) ->
                mapOf(
                    "value" to value,
                    "name" to arr[0],
                    "desc" to arr[1]
                )
            }

            val resultContext = baseMap.toMutableMap()

            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext["DEFAULT_ENUM_PACKAGE"] = "enums"
            resultContext.putContext(tag, "Enum", currentEnumType)

            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "CommentEscaped", "")
            resultContext.putContext(tag, "EnumValueField", getString("enumValueField"))
            resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))
            resultContext.putContext(tag, "EnumItems", enumItems)

            return resultContext
        }
    }

    override fun generatorFullName(table: Map<String, Any?>, context: EntityContext): String {
        with(context) {
            val defaultEnumPackage = "enums"

            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag]!!)
            val `package` = refPackage(aggregate)

            return "$basePackage${templatePackage}${`package`}${refPackage(defaultEnumPackage)}${refPackage(currentEnumType)}"
        }
    }

    override fun generatorName(table: Map<String, Any?>, context: EntityContext): String {
        return currentEnumType
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@EnumGenerator.tag
            name = "{{ DEFAULT_ENUM_PACKAGE }}{{ SEPARATOR }}{{ Enum }}.kt"
            format = "resource"
            data = "templates/enum.peb"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(
        table: Map<String, Any?>,
        context: EntityContext,
    ) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
