package com.only.codegen.generators.entity

import com.only.codegen.context.entity.EntityContext
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

/**
 * Schema 基类生成器
 * 生成包含 JPA Criteria API 辅助类的 Schema 基类
 */
class SchemaBaseGenerator : EntityTemplateGenerator {
    override val tag = "schema_base"
    override val order = 10

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean
        = !context.typeMapping.containsKey("Schema")

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(schemaPackage))
            resultContext.putContext(tag, "package", "")

            resultContext.putContext(tag, "SchemaBase", "Schema")
        }

        return resultContext
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@SchemaBaseGenerator.tag
            name = "{{ SchemaBase }}.kt"
            format = "resource"
            data = "templates/schema_base.peb"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        with(context) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(schemaPackage)

            typeMapping["Schema"] = "$basePackage$templatePackage${refPackage("Schema")}"
        }
    }
}
