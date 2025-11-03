package com.only4.codegen.template

/**
 * 提供模板节点的合并与选择逻辑，统一 GenAggregate/GenDesign 的重复实现。
 */
object TemplateMerger {

    /**
     * 将上下文模板与默认模板合并，并根据生成名选择匹配的一组顶层模板节点。
     */
    fun mergeAndSelect(
        ctxTop: List<TemplateNode>,
        defTop: List<TemplateNode>,
        genName: String,
    ): List<TemplateNode> {
        fun collectFiles(nodes: List<TemplateNode>): List<TemplateNode> =
            nodes.flatMap { it.collectFiles() }

        val ctxFiles = linkedMapOf<String, TemplateNode>()
        collectFiles(ctxTop).forEach { ctxFiles[it.uniqueKey()] = it }

        val defFiles = linkedMapOf<String, TemplateNode>()
        collectFiles(defTop).forEach { defFiles[it.uniqueKey()] = it }

        fun dirsByPattern(nodes: List<TemplateNode>): Map<String, TemplateNode> =
            buildMap {
                nodes.filter { it.isDirNode() }.forEach { d ->
                    if (!this.containsKey(d.pattern)) this[d.pattern] = d
                }
            }

        val ctxDirs = dirsByPattern(ctxTop)
        val defDirs = dirsByPattern(defTop)

        val allKeys: Set<String> = (ctxFiles.keys + defFiles.keys).toSet()
        val groups: List<TemplateNode> = allKeys.map { key ->
            val fileTpl = (ctxFiles[key] ?: defFiles[key])!!.deepCopy()
            val pattern = fileTpl.pattern
            val dirTpl = (ctxDirs[pattern] ?: defDirs[pattern])
            if (dirTpl != null) {
                val d = dirTpl.deepCopy()
                d.children = mutableListOf(fileTpl.toPathNode())
                d
            } else {
                fileTpl
            }
        }

        return groups.filter { it.matches(genName) }
    }
}

