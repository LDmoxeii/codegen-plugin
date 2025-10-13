package com.only.codegen.context.design.builders

import com.only.codegen.context.design.ClientDesign
import com.only.codegen.context.design.DesignContextBuilder
import com.only.codegen.context.design.MutableDesignContext
import com.only.codegen.misc.toUpperCamelCase
import org.gradle.api.logging.Logging

class ClientDesignBuilder : DesignContextBuilder {
    private val logger = Logging.getLogger(ClientDesignBuilder::class.java)
    override val order: Int = 20

    override fun build(context: MutableDesignContext) {
        val clientElements = context.designElementMap["cli"] ?: emptyList()
        clientElements.forEach { element ->
            try {
                val parts = element.name.split(".")
                val packagePath = if (parts.size > 1) parts.dropLast(1).joinToString(".") else (element.aggregate ?: "")
                val rawName = parts.lastOrNull() ?: element.name
                var clientName = toUpperCamelCase(rawName).orEmpty()
                if (!clientName.endsWith("Cli") && !clientName.endsWith("Client")) clientName += "Cli"
                val fullName = if (packagePath.isNotBlank()) "$packagePath.$clientName" else clientName

                val clientDesign = ClientDesign(
                    name = clientName,
                    fullName = fullName,
                    packagePath = packagePath,
                    aggregate = element.aggregate,
                    desc = element.desc,
                    requestName = "${clientName}Request",
                    responseName = "${clientName}Response"
                )
                context.clientDesignMap[clientDesign.fullName] = clientDesign
            } catch (e: Exception) {
                logger.error("Failed to build client design for: ${element.name}", e)
            }
        }
        logger.lifecycle("Built ${context.clientDesignMap.size} client designs")
    }
}
