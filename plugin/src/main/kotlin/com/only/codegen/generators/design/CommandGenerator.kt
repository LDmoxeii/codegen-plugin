package com.only.codegen.generators.design

import com.only.codegen.context.design.CommandDesign
import com.only.codegen.context.design.DesignContext
import com.only.codegen.misc.concatPackage
import com.only.codegen.misc.refPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

/**
 * 命令生成器
 *
 * Order: 10
 * 职责: 生成 Command + CommandHandler + Request/Response
 */
class CommandGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(CommandGenerator::class.java)

    override val tag: String = "command"
    override val order: Int = 10

    companion object {
        const val COMMAND_PACKAGE = "application.commands"
    }

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        if (design !is CommandDesign) return false

        // 避免重复生成
        if (context.typeMapping.containsKey(generatorName(design, context))) {
            return false
        }

        return true
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommandDesign) { "Design must be CommandDesign" }

        val resultContext = context.baseMap.toMutableMap()

        with(context) {
            // 模块和包路径
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "package", COMMAND_PACKAGE)
            resultContext.putContext(tag, "templatePackage", refPackage(COMMAND_PACKAGE))

            // 基本信息
            resultContext.putContext(tag, "Name", design.name)
            resultContext.putContext(tag, "Command", design.name)
            resultContext.putContext(tag, "Request", design.requestName)
            resultContext.putContext(tag, "Response", design.responseName)
            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "))

            // 主聚合信息 (单聚合场景)
            if (design.aggregate != null) {
                resultContext.putContext(tag, "Aggregate", design.aggregate)

                // 主聚合元数据 (自动提供)
                design.primaryAggregateMetadata?.let { aggMeta ->
                    resultContext.putContext(tag, "AggregateRoot", aggMeta.aggregateRoot.name)
                    resultContext.putContext(tag, "IdType", aggMeta.idType ?: "String")
                    resultContext["aggregateRootFullName"] = aggMeta.aggregateRoot.fullName
                    resultContext["aggregatePackage"] = aggMeta.packageName
                    resultContext["entities"] = aggMeta.entities  // 聚合内所有实体
                }
            }

            // 所有关联聚合信息 (多聚合场景)
            resultContext["aggregates"] = design.aggregates
            resultContext["aggregateMetadataList"] = design.aggregateMetadataList

            // 是否跨聚合
            resultContext["crossAggregate"] = design.aggregates.size > 1
        }

        return resultContext
    }

    override fun generatorFullName(design: Any, context: DesignContext): String {
        require(design is CommandDesign)
        val basePackage = context.getString("basePackage")
        val fullPackage = concatPackage(basePackage, COMMAND_PACKAGE, design.packagePath)
        return concatPackage(fullPackage, design.name)
    }

    override fun generatorName(design: Any, context: DesignContext): String {
        require(design is CommandDesign)
        return design.name
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode().apply {
            type = "file"
            tag = this@CommandGenerator.tag
            name = "{{ Name }}.kt"
            format = "resource"
            data = "templates/application/command/Command.peb"
            conflict = "skip"
        }
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is CommandDesign) {
            val fullName = generatorFullName(design, context)
            context.typeMapping[generatorName(design, context)] = fullName
            logger.lifecycle("Generated command: $fullName")
        }
    }
}
