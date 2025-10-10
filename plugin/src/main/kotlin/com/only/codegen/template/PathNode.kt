package com.only.codegen.template

import com.only.codegen.misc.concatPathOrHttpUri
import com.only.codegen.misc.isAbsolutePathOrHttpUri
import com.only.codegen.misc.loadFileContent
import com.only.codegen.pebble.PebbleTemplateRenderer.renderString

/**
 * 脚手架模板文件节点
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
open class PathNode {
    /**
     * 节点类型：root|dir|file|segment
     */
    var type: String? = null

    /**
     * 节点标签：关联模板
     */
    var tag: String? = null

    /**
     * 节点名称
     */
    var name: String? = null

    /**
     * 模板源类型：raw|url
     */
    var format: String = "raw"

    /**
     * 输出编码
     */
    var encoding: String? = null

    /**
     * 模板数据
     */
    var data: String? = null

    /**
     * 冲突处理：skip|warn|overwrite
     */
    var conflict: String = "skip"

    /**
     * 下级节点
     */
    var children: MutableList<PathNode>? = null

    companion object {
        private val directory = ThreadLocal<String>()
        fun setDirectory(dir: String) = directory.set(dir)
        fun clearDirectory() = directory.remove()
        fun getDirectory(): String = directory.get()
    }

    open fun resolve(context: Map<String, Any?>): PathNode {
        name = name
            ?.replace("{{ basePackage }}", "{{ basePackage__as_path }}")
            ?.let { renderString(it, context) }

        val rawData = when (format.lowercase()) {
            "url" -> data?.let { src ->
                val abs = if (isAbsolutePathOrHttpUri(src)) src else concatPathOrHttpUri(directory.get(), src)
                loadFileContent(abs, context["archTemplateEncoding"]?.toString() ?: "UTF-8")
            } ?: ""

            else -> data ?: ""
        }

        data = renderString(rawData, context)
        format = "raw"

        children?.forEach { it.resolve(context) }
        return this
    }
}
