package com.only.codegen.generators.design

import com.only.codegen.context.design.ClientDesign
import com.only.codegen.context.design.DesignContext
import com.only.codegen.template.TemplateNode

class ClientGenerator : DesignTemplateGenerator {
    override val tag = "client"
    override val order = 10
    override fun shouldGenerate(design: Any, context: DesignContext) = design is ClientDesign
    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is ClientDesign)
        return mutableMapOf<String, Any?>().apply {
            this["Name"] = design.name
            this["modulePath"] = context.applicationPath
            this["templatePackage"] = "application.clients"
        }
    }
    override fun getDefaultTemplateNode() = TemplateNode(type = "file", name = "{{ Name }}.kt", conflict = "skip", tag = tag)
}
