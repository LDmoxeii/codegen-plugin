package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.DomainServiceDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

class DomainServiceGenerator : DesignTemplateGenerator {

    override val tag = "domain_service"
    override val order = 20

    companion object {
        const val DOMAIN_SERVICE_PACKAGE = "domain.services"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is DomainServiceDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is DomainServiceDesign)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "package", DOMAIN_SERVICE_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(DOMAIN_SERVICE_PACKAGE))
            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "DomainService", design.name)
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is DomainServiceDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, DOMAIN_SERVICE_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is DomainServiceDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@DomainServiceGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/domain/service/DomainService.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is DomainServiceDesign) {
            context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
        }
    }
}
