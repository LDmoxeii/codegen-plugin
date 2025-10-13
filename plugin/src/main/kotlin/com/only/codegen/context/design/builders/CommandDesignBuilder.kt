package com.only.codegen.context.design.builders

import com.only.codegen.context.design.CommandDesign
import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

/**
 * 命令设计构建器
 *
 * Order: 20
 * 职责: 解析命令设计元素,生成 CommandDesign 对象
 */
class CommandDesignBuilder : DesignContextBuilder {

    private val logger = Logging.getLogger(CommandDesignBuilder::class.java)

    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val commandElements = context.designElementMap["cmd"] ?: emptyList()

        commandElements.forEach { element ->
            try {
                val commandDesign = buildCommandDesign(element, context)
                context.commandDesignMap[commandDesign.fullName] = commandDesign
            } catch (e: Exception) {
                logger.error("Failed to build command design for: ${element.name}", e)
            }
        }

        logger.lifecycle("Built ${context.commandDesignMap.size} command designs")
    }

    private fun buildCommandDesign(
        element: com.only.codegen.context.design.DesignElement,
        context: MutableDesignContext
    ): CommandDesign {
        // 解析 name: "category.CreateCategory" 或 "CreateCategory"
        val parts = element.name.split(".")
        val packagePath = if (parts.size > 1) {
            parts.dropLast(1).joinToString(".")
        } else {
            element.aggregate ?: ""
        }

        val rawName = parts.lastOrNull() ?: element.name
        var commandName = toUpperCamelCase(rawName).orEmpty()

        // 自动添加 Cmd 后缀
        if (!commandName.endsWith("Cmd") && !commandName.endsWith("Command")) {
            commandName += "Cmd"
        }

        val fullName = if (packagePath.isNotBlank()) {
            "$packagePath.$commandName"
        } else {
            commandName
        }

        val requestName = "${commandName}Request"
        val responseName = "${commandName}Response"

        // 尝试关联聚合元数据
        val aggregateMetadata = element.aggregate?.let { aggName ->
            context.aggregateMetadataMap[aggName]
        }

        return CommandDesign(
            name = commandName,
            fullName = fullName,
            packagePath = packagePath,
            aggregate = element.aggregate,
            desc = element.desc,
            requestName = requestName,
            responseName = responseName,
            aggregateMetadata = aggregateMetadata
        )
    }
}
