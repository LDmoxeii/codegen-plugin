package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.IntegrationEventDesign
import com.only.codegen.template.TemplateNode

class IntegrationEventGenerator : DesignTemplateGenerator {
    override val tag = "integration_event"
    override val order = 20
    override fun shouldGenerate(design: Any, context: DesignContext) = design is IntegrationEventDesign
    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is IntegrationEventDesign)
        return mutableMapOf<String, Any?>().apply {
            this["Name"] = design.name
            this["modulePath"] = context.applicationPath
            this["templatePackage"] = "application.events"
        }
    }
    override fun getDefaultTemplateNode() = TemplateNode(type = "file", name = "{{ Name }}.kt", conflict = "skip", tag = tag)
}
