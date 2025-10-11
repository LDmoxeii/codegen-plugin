package com.only.codegen.generators

import com.only.codegen.context.EntityContext
import com.only.codegen.misc.SqlSchemaUtils
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toLowerCamelCase
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode
import java.io.File

/**
 * 枚举文件生成器
 */
class EnumGenerator : TemplateGenerator {
    override val tag = "enum"
    override val order = 10

    private val generatedEnums = mutableSetOf<String>()

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = context.entityTypeMap[tableName]

        val columns = context.columnsMap[tableName] ?: return false

        return columns.any { column ->
            SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column) && generatedEnums.contains(entityType)
        }
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        with(context) {

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName]!!
            val aggregate = resolveAggregateWithModule(tableName)
            val tablePackage = tablePackageMap[tableName] ?: ""
            val entityType = entityTypeMap[tableName] ?: return emptyMap()
            val entityVar = toLowerCamelCase(entityType) ?: entityType

            // 收集本表的所有未生成的枚举
            val newEnums = mutableListOf<String>()

            columns.forEach { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumType = SqlSchemaUtils.getType(column)
                    if (generatedEnums.contains(enumType)) return@forEach
                    val enumConfig = enumConfigMap[enumType]
                    if (enumConfig != null && enumConfig.isNotEmpty()) {
                        newEnums.add(enumType)
                        generatedEnums.add(enumType)
                    }

                }
            }

            // 如果没有新的枚举需要生成，返回空
            if (newEnums.isEmpty()) {
                return emptyMap()
            }

            val enumType = newEnums.first()
            val enumConfig = enumConfigMap[enumType]!!


            val enumItems = enumConfig.toSortedMap().map { (value, arr) ->
                mapOf(
                    "value" to value,
                    "name" to arr[0],
                    "desc" to arr[1]
                )
            }

            val resultContext = baseMap.toMutableMap()
            resultContext["DEFAULT_ENUM_PACKAGE"] = "enums"

            resultContext.putContext(tag, "templatePackage", refPackage(aggregatesPackage))
            resultContext.putContext(tag, "package", refPackage(aggregate))
            resultContext.putContext(tag, "path", aggregate.replace(".", File.separator))
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", "")
            resultContext.putContext(tag, "CommentEscaped", "")
            resultContext.putContext(tag, "entityPackage", refPackage(tablePackage, getString("basePackage")))
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "AggregateRoot", entityType)
            resultContext.putContext(tag, "EntityVar", entityVar)
            resultContext.putContext(tag, "Enum", enumType)
            resultContext.putContext(tag, "EnumValueField", getString("enumValueField"))
            resultContext.putContext(tag, "EnumNameField", getString("EnumNameField"))
            resultContext.putContext(tag, "EnumItems", enumItems)

            return resultContext
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@EnumGenerator.tag
            name = "{{ path }}{{ SEPARATOR }}{{ DEFAULT_ENUM_PACKAGE }}{{ SEPARATOR }}{{ Enum }}.kt"
            format = "resouce"
            data = "enum"
            conflict = "overwrite"
        }
    }
}
