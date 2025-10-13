package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.SagaDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.template.TemplateNode
import java.io.File

class SagaGenerator : DesignTemplateGenerator {
    override val tag = "saga"
    override val order = 10
    override fun shouldGenerate(design: Any, context: DesignContext) = design is SagaDesign
    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is SagaDesign)
        return mutableMapOf<String, Any?>().apply {
            this["Name"] = design.name
            this["modulePath"] = context.applicationPath
            this["templatePackage"] = "application.sagas"
        }
    }
    override fun getDefaultTemplateNode() = TemplateNode(type = "file", name = "{{ Name }}.kt", conflict = "skip", tag = tag)
}
