package com.only.codegen.generators.design

import com.only.codegen.context.design.CommandDesign
import com.only.codegen.context.design.DesignContext
import com.only.codegen.misc.concatPackage
import com.only.codegen.template.TemplateNode
import org.gradle.api.logging.Logging
import java.io.File

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

    override fun shouldGenerate(design: Any, context: DesignContext): Boolean {
        return design is CommandDesign
    }

    override fun buildContext(design: Any, context: DesignContext): Map<String, Any?> {
        require(design is CommandDesign) { "Design must be CommandDesign" }

        val contextMap = mutableMapOf<String, Any?>()

        // 基本信息
        contextMap.putContext(tag, "Name", design.name, context)
        contextMap.putContext(tag, "Command", design.name, context)
        contextMap.putContext(tag, "Request", design.requestName, context)
        contextMap.putContext(tag, "Response", design.responseName, context)
        contextMap.putContext(tag, "Comment", design.desc, context)
        contextMap.putContext(tag, "CommentEscaped", design.desc.replace(Regex("\\r\\n|[\\r\\n]"), " "), context)

        // 包路径信息
        contextMap.putContext(tag, "path", design.packagePath.replace(".", File.separator), context)
        contextMap.putContext(tag, "package", if (design.packagePath.isNotBlank()) ".${design.packagePath}" else "", context)

        // 聚合信息
        if (design.aggregate != null) {
            contextMap.putContext(tag, "Aggregate", design.aggregate, context)

            // 如果有聚合元数据,提供更多信息
            design.aggregateMetadata?.let { aggMeta ->
                contextMap.putContext(tag, "AggregateRoot", aggMeta.aggregateRoot.name, context)
                contextMap.putContext(tag, "IdType", aggMeta.idType ?: "String", context)
            }
        }

        // 模块路径 (application 层)
        contextMap["modulePath"] = context.applicationPath
        contextMap["templatePackage"] = "application.commands"

        return contextMap
    }

    override fun getDefaultTemplateNode(): TemplateNode {
        return TemplateNode(
            type = "file",
            name = "{{ Name }}.kt",
            conflict = "skip",
            tag = tag,
            encoding = null,
            pattern = null,
            templatePath = "templates/application/command/Command.peb"
        )
    }

    override fun onGenerated(design: Any, context: DesignContext) {
        if (design is CommandDesign) {
            val basePackage = context.getString("basePackage")
            val fullPackage = concatPackage(basePackage, "application.commands", design.packagePath)
            val fullName = concatPackage(fullPackage, design.name)
            context.typeMapping[design.name] = fullName

            logger.lifecycle("Generated command: $fullName")
        }
    }

    private fun MutableMap<String, Any?>.putContext(
        tag: String,
        variable: String,
        value: Any,
        context: DesignContext
    ) {
        // 使用 BaseContext 的别名映射系统
        val key = "$tag.$variable"
        val aliases = context.templateAliasMap[key] ?: listOf(variable)
        aliases.forEach { alias ->
            this[alias] = value
        }
    }
}
