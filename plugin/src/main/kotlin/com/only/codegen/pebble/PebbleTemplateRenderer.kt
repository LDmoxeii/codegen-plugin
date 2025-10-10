package com.only.codegen.pebble

import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter

object PebbleTemplateRenderer {

    fun renderString(
        templateContent: String,
        context: Map<String, Any?>,
    ): String {
        val engine = ensureInitialized()

        val writer = StringWriter()

        val template = engine.getTemplate(templateContent)
        template.evaluate(writer, context)
        return writer.toString()
    }

    /**
     * 确保 Pebble 引擎已初始化
     */
    private fun ensureInitialized(): PebbleEngine {
        if (!PebbleInitializer.isInitialized()) {
            PebbleInitializer.initPebble()
        }
        return PebbleInitializer.getEngine()
    }
}
