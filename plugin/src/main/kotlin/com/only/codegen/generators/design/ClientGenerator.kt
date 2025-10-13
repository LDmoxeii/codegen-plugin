package com.only.codegen.generators.design

import com.only.codegen.context.design.ClientDesign
import com.only.codegen.context.design.DesignContext
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

class ClientGenerator : DesignTemplateGenerator {

    override val tag = "client"
    override val order = 10

    companion object {
        const val CLIENT_PACKAGE = "application.clients"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is ClientDesign) return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is ClientDesign)

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", CLIENT_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(CLIENT_PACKAGE))
            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "Client", design.name)
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is ClientDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, CLIENT_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is ClientDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@ClientGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/application/client/Client.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is ClientDesign) {
            context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
        }
    }
}
