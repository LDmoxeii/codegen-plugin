package com.only.codegen.generators.design

import com.only.codegen.context.design.CommonDesign
import com.only.codegen.context.design.DesignContext
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class ClientGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(ClientGenerator::class.java)

    override val tag: String = "client"
    override val order: Int = 10

    companion object {
        const val CLIENT_PACKAGE = "domain.clients"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is CommonDesign || design.type != "cli") return false
        if (context.typeMapping.containsKey(generatorName(design, context))) return false
        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommonDesign && design.type == "cli")

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "package", CLIENT_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(CLIENT_PACKAGE))

            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "Client", design.name)
            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\r\n|[\r\n]"), " "))

            if (design.aggregate != null) {
                resultContext.putContext(tag, "Aggregate", design.aggregate)
                design.primaryAggregateMetadata?.let { aggMeta ->
                    resultContext.putContext(tag, "AggregateRoot", aggMeta.aggregateRoot.name)
                    resultContext.putContext(tag, "IdType", aggMeta.idType ?: "String")
                    resultContext["aggregateRootFullName"] = aggMeta.aggregateRoot.fullName
                    resultContext["aggregatePackage"] = aggMeta.packageName
                    resultContext["entities"] = aggMeta.entities
                }
            }

            resultContext["aggregates"] = design.aggregates
            resultContext["aggregateMetadataList"] = design.aggregateMetadataList
            resultContext["crossAggregate"] = design.aggregates.size > 1
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, CLIENT_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@ClientGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/client.kt.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is CommonDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
            logger.lifecycle("Generated client: $fullName")
        }
    }
}
