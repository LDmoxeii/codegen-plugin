package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.SagaDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

class SagaGenerator : DesignTemplateGenerator {

    override val tag = "saga"
    override val order = 10

    companion object {
        const val SAGA_PACKAGE = "application.sagas"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is SagaDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is SagaDesign)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", SAGA_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(SAGA_PACKAGE))
            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "Saga", design.name)
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is SagaDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, SAGA_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is SagaDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@SagaGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/application/saga/Saga.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is SagaDesign) {
            context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
        }
    }
}
