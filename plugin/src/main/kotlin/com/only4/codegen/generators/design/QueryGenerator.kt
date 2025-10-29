package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.CommonDesign
import com.only4.codegen.manager.QueryImportManager
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class QueryGenerator : DesignTemplateGenerator {

    override val tag: String = "query"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is CommonDesign) return false
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val resultContext = ctx.baseMap.toMutableMap()

        // 根据设计名称推断查询类型
        val queryType = QueryImportManager.inferQueryType(design.name)

        // 创建 ImportManager
        val importManager = QueryImportManager(queryType)
        importManager.addBaseImports()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Query", generatorName(design))
            resultContext.putContext(tag, "Comment", design.desc)

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is CommonDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)

        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is CommonDesign)
        val name = design.name
        return if (name.endsWith("Qry")) {
            toUpperCamelCase(name)!!
        } else {
            toUpperCamelCase("${name}Qry")!!
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                pattern = "^(?!.*(List|list|Page|page)).*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                pattern = "^.*(List|list).*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query_list.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                pattern = "^.*(Page|page).*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query_page.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is CommonDesign) {
            val fullName = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = fullName
        }
    }
}
