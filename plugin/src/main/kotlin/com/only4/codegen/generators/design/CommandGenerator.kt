package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.CommonDesign
import com.only4.codegen.manager.CommandImportManager
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class CommandGenerator : DesignTemplateGenerator {

    override val tag: String = "command"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean =
        !ctx.typeMapping.containsKey(generatorName(design))

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val resultContext = ctx.baseMap.toMutableMap()

        // 创建 ImportManager
        val importManager = CommandImportManager()
        importManager.addBaseImports()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Command", generatorName(design))
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
        return if (name.endsWith("Cmd")) {
            toUpperCamelCase(name)!!
        } else {
            toUpperCamelCase("${name}Cmd")!!
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@CommandGenerator.tag
                name = "{{ Command }}.kt"
                format = "resource"
                data = "templates/command.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        ctx.typeMapping[generatorName(design)] = generatorFullName(design)
    }
}
