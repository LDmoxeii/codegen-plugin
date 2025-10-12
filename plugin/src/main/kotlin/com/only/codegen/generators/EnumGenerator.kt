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

    @Volatile
    private lateinit var currentEnumType: String

    private val generated = mutableSetOf<String>()

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
                    !(generated.contains(enumType))
                }
            }
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

            columns.first { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumType = SqlSchemaUtils.getType(column)
                    val enumConfig = enumConfigMap[enumType]
                    if (!(generated.contains(entityType)) && enumConfig != null && enumConfig.isNotEmpty()) {
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
            resultContext.putContext(tag, "templatePackage", refPackage(aggregatesPackage))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext["DEFAULT_ENUM_PACKAGE"] = "enums"
            resultContext.putContext(tag, "path", aggregate.replace(".", File.separator))

            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", "")
            resultContext.putContext(tag, "CommentEscaped", "")
            resultContext.putContext(tag, "entityPackage", refPackage(tablePackage, getString("basePackage")))
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "AggregateRoot", entityType)
            resultContext.putContext(tag, "EntityVar", entityVar)
            resultContext.putContext(tag, "Enum", currentEnumType)
            resultContext.putContext(tag, "EnumValueField", getString("enumValueField"))
            resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))
            resultContext.putContext(tag, "EnumItems", enumItems)

            return resultContext
        }
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@EnumGenerator.tag
            name = "{{ path }}{{ SEPARATOR }}{{ DEFAULT_ENUM_PACKAGE }}{{ SEPARATOR }}{{ Enum }}.kt"
            format = "resource"
            data = "enum"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(
        table: Map<String, Any?>,
        context: EntityContext,
    ) {
        with(context) {
            val defaultEnumPackage = "enums"

            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            // 计算枚举包路径
            val enumPackageSuffix = buildString {
                val packageName = templateNodeMap["enum"]
                    ?.takeIf { it.isNotEmpty() }
                    ?.get(0)?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultEnumPackage

                if (packageName.isNotBlank()) {
                    append(".$packageName")
                }
            }

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(aggregatesPackage)
            val `package` = refPackage(aggregate)

            val fullEnumType = "$basePackage${templatePackage}${`package`}$enumPackageSuffix.${currentEnumType}"

            typeRemapping[currentEnumType] = fullEnumType
            generated.add(currentEnumType)
        }

    }
}
