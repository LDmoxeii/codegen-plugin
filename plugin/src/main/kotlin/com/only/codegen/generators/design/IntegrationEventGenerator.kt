package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.IntegrationEventDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

class IntegrationEventGenerator : DesignTemplateGenerator {

    override val tag = "integration_event"
    override val order = 20

    companion object {
        const val INTEGRATION_EVENT_PACKAGE = "application.events"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is IntegrationEventDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is IntegrationEventDesign)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", INTEGRATION_EVENT_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(INTEGRATION_EVENT_PACKAGE))
            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "IntegrationEvent", design.name)
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is IntegrationEventDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, INTEGRATION_EVENT_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is IntegrationEventDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@IntegrationEventGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/application/event/IntegrationEvent.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is IntegrationEventDesign) {
            context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
        }
    }
}
