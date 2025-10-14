package com.only.codegen.generators.design

import com.only.codegen.context.design.DesignContext
import com.only.codegen.context.design.models.CommonDesign
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode

class CommandGenerator : DesignTemplateGenerator {

    override val tag: String = "command"
    override val order: Int = 10

    companion object {
        const val COMMAND_PACKAGE = "application.commands"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is CommonDesign || design.type != "cmd") return false

        if (context.typeMapping.containsKey(generatorName(design, context))) {
            return false
        }

        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommonDesign && design.type == "cmd") { "Design must be CommonDesign with type=cmd" }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", COMMAND_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(COMMAND_PACKAGE))

            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "Command", design.name)
            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "))

            if (design.aggregate != null) {
                resultContext.putContext(tag, "Aggregate", design.aggregate)

                design.primaryAggregateMetadata?.let { aggMeta ->
                    resultContext.putContext(tag, "AggregateRoot", aggMeta.aggregateRoot.className)
                    resultContext.putContext(tag, "IdType", aggMeta.identityType)
                    resultContext["aggregateRootFullName"] = aggMeta.aggregateRoot.qualifiedName
                    resultContext["aggregatePackage"] = aggMeta.modulePath
                    resultContext["entities"] = aggMeta.entities  // 聚合内所有实体
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
        val fullPackage = concatPackage(basePackage, COMMAND_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommonDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@CommandGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/command.kt.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        context.typeMapping[generatorName(design, context)] = generatorFullName(design, context)
    }
}
