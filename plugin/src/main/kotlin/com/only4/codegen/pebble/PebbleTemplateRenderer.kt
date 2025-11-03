package com.only4.codegen.pebble

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import java.io.StringWriter

object PebbleTemplateRenderer {

    /**
     * 渲染字符串模板（支持递归解析）。为减少隐式全局状态，每次渲染使用短生命周期的引擎。
     */
    fun renderString(
        templateContent: String,
        context: Map<String, Any?>,
    ): String {
        val engine = newEngine()
        var result = templateContent
        var iterations = 0
        val maxIterations = 10

        // 正则匹配 Pebble 语法：{{ ... }} 或 {% ... %}
        val pebblePattern = Regex("""(\{\{.*?}}|\{%.*?%})""")

        while (pebblePattern.containsMatchIn(result) && iterations < maxIterations) {
            val writer = StringWriter()
            val template = engine.getTemplate(result)
            template.evaluate(writer, context)

            val newResult = writer.toString()

            if (newResult == result) break

            result = newResult
            iterations++
        }

        return result
    }

    private fun newEngine(): PebbleEngine =
        PebbleEngine.Builder()
            .loader(StringLoader())
            .strictVariables(false)
            .cacheActive(false)
            .autoEscaping(false)
            .newLineTrimming(false)
            .build()
}
