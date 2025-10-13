package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.DomainServiceDesign
import com.only.codegen.template.TemplateNode

class DomainServiceGenerator : DesignTemplateGenerator {
    override val tag = "domain_service"
    override val order = 20
    override fun shouldGenerate(design: Any, context: DesignContext) = design is DomainServiceDesign
    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is DomainServiceDesign)
        return mutableMapOf<String, Any?>().apply {
            this["Name"] = design.name
            this["modulePath"] = context.domainPath
            this["templatePackage"] = "domain.services"
        }
    }
    override fun getDefaultTemplateNode() = TemplateNode(type = "file", name = "{{ Name }}.kt", conflict = "skip", tag = tag)
}
