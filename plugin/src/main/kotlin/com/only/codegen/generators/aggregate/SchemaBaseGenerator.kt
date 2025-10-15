package com.only.codegen.generators.aggregate

import com.only.codegen.context.aggregate.AggregateContext
import com.only.codegen.manager.SchemaBaseImportManager
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

/**
 * Schema 基类生成器
 * 生成包含 JPA Criteria API 辅助类的 Schema 基类
 */
class SchemaBaseGenerator : AggregateTemplateGenerator {
    override val tag = "schema_base"
    override val order = 10

    override fun shouldGenerate(table: Map<String, Any?>, context: AggregateContext): Boolean
        = !context.typeMapping.containsKey(generatorName(table, context))

    override fun buildContext(table: Map<String, Any?>, context: AggregateContext): Map<String, Any?> {
        val resultContext = context.baseMap.toMutableMap()

        // 创建 ImportManager
        val importManager = SchemaBaseImportManager()
        importManager.addBaseImports()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", "")

            resultContext.putContext(tag, "SchemaBase", generatorName(table, context))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    override fun generatorFullName(
        table: Map<String, Any?>,
        context: AggregateContext
    ): String {
        with(context) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = ""

            return "$basePackage$templatePackage$`package`${refPackage(generatorName(table, context))}"
        }
    }

    override fun generatorName(
        table: Map<String, Any?>,
        context: AggregateContext
    ): String = "Schema"

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SchemaBaseGenerator.tag
                name = "{{ SchemaBase }}.kt"
                format = "resource"
                data = "templates/schema_base.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    override fun onGenerated(table: Map<String, Any?>, context: AggregateContext) {
        context.typeMapping[generatorName(table, context)] = generatorFullName(table, context)
    }
}
