package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.models.CommonDesign
import com.only.codegen.manager.QueryHandlerImportManager
import com.only.codegen.misc.refPackage
import com.only.codegen.misc.toUpperCamelCase
import com.only.codegen.template.TemplateNode

class QueryHandlerGenerator : DesignTemplateGenerator {

    override val tag: String = "query_handler"
    override val order: Int = 20

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is CommonDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val resultContext = context.baseMap.toMutableMap()
        val queryType = context.typeMapping[getQueryName(design, context)]!!

        // 根据设计名称推断查询类型
        val handlerQueryType = QueryHandlerImportManager.inferQueryType(design.name)

        // 创建 ImportManager
        val importManager = QueryHandlerImportManager(handlerQueryType)
        importManager.addBaseImports()
        importManager.add(queryType)

        with(context) {
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "QueryHandler", generatorName(design, context))
            resultContext.putContext(tag, "Query", getQueryName(design, context))

            resultContext.putContext(tag, "Comment", design.desc)

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        with(context) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(design.`package`)

            return "$basePackage$templatePackage$`package`${refPackage(generatorName(design, context))}"
        }
    }

    private fun getQueryName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        val name = design.name
        return if (name.endsWith("Qry")) {
            toUpperCamelCase(name)!!
        } else {
            toUpperCamelCase("${name}Qry")!!
        }
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        val queryName = getQueryName(design, context)
        return toUpperCamelCase("${queryName}Handler")!!
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@QueryHandlerGenerator.tag
                pattern = "^(?!.*(List|list|Page|page)).*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/query_handler.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryHandlerGenerator.tag
                pattern = "^.*(List|list).*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/query_list_handler.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryHandlerGenerator.tag
                pattern = "^.*(Page|page).*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/query_page_handler.kt.peb"
                conflict = "skip"
            }
        )
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is CommonDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
        }
    }
}
