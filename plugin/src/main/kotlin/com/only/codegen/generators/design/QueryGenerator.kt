package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.models.CommonDesign
import com.only.codegen.manager.QueryImportManager
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class QueryGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(QueryGenerator::class.java)

    override val tag: String = "query"
    override val order: Int = 10

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is CommonDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val resultContext = context.baseMap.toMutableMap()

        // 创建 ImportManager
        val importManager = QueryImportManager()
        importManager.addBaseImports()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Query", generatorName(design, context))
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

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        val name = design.name
        return if (name.endsWith("Qry")) {
            name
        } else {
            "${name}Qry"
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query.kt.peb"
                conflict = "skip"
            }
        )
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is CommonDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
            logger.lifecycle("Generated query: $fullName")
        }
    }
}
