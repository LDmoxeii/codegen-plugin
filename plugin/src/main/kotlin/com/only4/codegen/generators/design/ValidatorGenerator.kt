package com.only4.codegen.generators.design

import com.only4.codegen.context.design.DesignContext
import com.only4.codegen.context.design.models.CommonDesign
import com.only4.codegen.misc.refPackage
import com.only4.codegen.misc.toUpperCamelCase
import com.only4.codegen.template.TemplateNode

class ValidatorGenerator : DesignTemplateGenerator {

    override val tag: String = "validator"
    override val order: Int = 10

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        return !context.typeMapping.containsKey(generatorName(design, context))
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommonDesign) { "Design must be CommonDesign" }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            // 将校验器固定输出到 application.validater 包下
            resultContext.putContext(tag, "templatePackage", refPackage("application"))
            resultContext.putContext(tag, "package", refPackage("validater"))

            resultContext.putContext(tag, "Validator", generatorName(design, context))
            resultContext.putContext(tag, "Comment", design.desc)

            // 值类型（默认 Long）
            resultContext.putContext(tag, "ValueType", "Long")
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        with(context) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage("application")
            val `package` = refPackage("validater")
            return "$basePackage$templatePackage$`package`${refPackage(generatorName(design, context))}"
        }
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        val name = design.name
        return toUpperCamelCase(name)!!
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@ValidatorGenerator.tag
                name = "{{ Validator }}.kt"
                format = "resource"
                data = "templates/validator.kt.peb"
                conflict = "skip"
            }
        )
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
    }
}
