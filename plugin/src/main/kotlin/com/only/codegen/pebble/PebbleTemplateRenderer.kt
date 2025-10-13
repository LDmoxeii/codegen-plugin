package com.only.codegen.pebble

import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter

object PebbleTemplateRenderer {

    /**
     * 渲染字符串模板
     *
     * 用于渲染纯模板字符串内容
     *
     * @param templateContent 模板内容字符串
     * @param context 模板上下文
     * @return 渲染后的字符串
     */
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
