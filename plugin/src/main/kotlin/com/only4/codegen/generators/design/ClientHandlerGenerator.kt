package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.CommonDesign
import com.only4.codegen.manager.ClientHandlerImportManager
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class ClientHandlerGenerator : DesignTemplateGenerator {

    override val tag: String = "client_handler"
    override val order: Int = 20

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
        val clientName = getClientName(design)
        val clientType = ctx.typeMapping[clientName]!!

        // 创建 ImportManager
        val importManager = ClientHandlerImportManager()
        importManager.addBaseImports()
        importManager.add(clientType)

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            // 模板使用 {{ Client }} 作为基名，类名为 {{ Client }}Handler
            resultContext.putContext(tag, "Client", clientName)
            resultContext.putContext(tag, "Comment", design.desc)

            // imports
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
    private fun getClientName(design: Any): String {
        require(design is CommonDesign)
        val name = design.name
        return if (name.endsWith("Cli")) {
            toUpperCamelCase(name)!!
        } else {
            toUpperCamelCase("${name}Cli")!!
        }
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        val client = getClientName(design)
        return toUpperCamelCase("${client}Handler")!!
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@ClientHandlerGenerator.tag
                name = "{{ Client }}Handler.kt"
                format = "resource"
                data = "templates/client_handler.kt.peb"
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

