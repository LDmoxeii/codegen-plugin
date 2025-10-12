package com.only.codegen.generators

import com.only.codegen.context.EntityContext
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

/**
 * Schema 基类生成器
 * 生成包含 JPA Criteria API 辅助类的 Schema 基类
 */
class SchemaBaseGenerator : TemplateGenerator {
    override val tag = "schema_base"
    override val order = 5

    @Volatile
    private var generated = false

    override fun shouldGenerate(table: Map<String, Any?>, context: EntityContext): Boolean {
        // Schema 基类只生成一次
        return !generated && context.getBoolean("generateSchema", false)
    }

    override fun buildContext(table: Map<String, Any?>, context: EntityContext): Map<String, Any?> {
        val schemaFullPackage = concatPackage(context.getString("basePackage"), context.schemaPackage)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(
                tag,
                "templatePackage",
                refPackage(schemaFullPackage, context.getString("basePackage"))
            )
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
            data = "schema_base"
            conflict = "overwrite"
        }
    }

    override fun onGenerated(table: Map<String, Any?>, context: EntityContext) {
        generated = true
    }
}
