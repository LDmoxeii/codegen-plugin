package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.models.CommonDesign
import com.only.codegen.misc.distinctText
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

class CommandGenerator : DesignTemplateGenerator {

    override val tag: String = "command"
    override val order: Int = 10

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean =
        !context.typeMapping.containsKey(generatorName(design, context))

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Command", generatorName(design, context))
            resultContext.putContext(tag, "Comment", design.desc)
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        with(context) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag]!!)
            val `package` = refPackage(design.`package`)

            return "$basePackage$templatePackage$`package`${refPackage(generatorName(design, context))}"
        }
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        val name = design.name
        return if (name.endsWith("Cmd")) {
            name
        } else {
            "${name}Cmd"
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

    override fun onGenerated(design: Any, context: DesignContext) {
        context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
    }
}
