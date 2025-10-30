package com.only4.codegen.template

import com.alibaba.fastjson.JSON

/**
 * 脚手架模板模板节点
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
class TemplateNode : PathNode() {

    /**
     * 元素匹配正则
     */
    var pattern: String = ""

    fun nodeType(): String = (this.type ?: "file").lowercase()
    fun isRootNode(): Boolean = nodeType() == "root"
    fun isDirNode(): Boolean = nodeType() == "dir"
    fun isFileNode(): Boolean = nodeType() == "file"
    fun uniqueKey(): String = "${this.name ?: ""}#${this.pattern}"
    fun matches(genName: String): Boolean = this.pattern.isBlank() ||
        java.util.regex.Pattern.compile(this.pattern).asPredicate().test(genName)

    /**
     * 收集顶层 file 节点以及作为当前节点(通常为 dir)子项中的所有 file 节点。
     * 对于子项中的 file(PathNode)，用当前节点的 pattern 包装成临时 TemplateNode，便于上层以 name+pattern 进行去重与合并。
     */
    fun collectFiles(): List<TemplateNode> {
        val result = mutableListOf<TemplateNode>()

        // 顶层（本身）是 file 的情况
        if (isFileNode()) {
            result.add(this)
        }

        // 子项遍历（只会是 PathNode）
        fun wrapPathFile(path: PathNode): TemplateNode {
            val t = TemplateNode()
            t.type = path.type
            t.tag = null
            t.name = path.name
            t.format = path.format
            t.encoding = path.encoding
            t.data = path.data
            t.conflict = path.conflict
            t.children = path.children // file 通常无子项；保留以防扩展
            t.pattern = this.pattern
            return t
        }

        fun dfsChildren(node: PathNode?) {
            if (node == null) return
            when (node.type?.lowercase()) {
                "file" -> result.add(wrapPathFile(node))
                "dir", "root", null -> node.children?.forEach { dfsChildren(it) }
                else -> node.children?.forEach { dfsChildren(it) }
            }
        }

        this.children?.forEach { dfsChildren(it) }
        return result
    }

    /**
     * 将 TemplateNode 转换为普通 PathNode（用于作为子节点渲染，避免子节点出现 TemplateNode 类型）。
     */
    fun toPathNode(): PathNode {
        val p = PathNode()
        p.type = this.type
        p.tag = null
        p.name = this.name
        p.format = this.format
        p.encoding = this.encoding
        p.data = this.data
        p.conflict = this.conflict
        // 子项一般不存在 TemplateNode；如存在则递归转换
        p.children = this.children?.map { ch ->
            when (ch) {
                is TemplateNode -> ch.toPathNode()
                else -> {
                    val cp = PathNode()
                    cp.type = ch.type
                    cp.tag = ch.tag
                    cp.name = ch.name
                    cp.format = ch.format
                    cp.encoding = ch.encoding
                    cp.data = ch.data
                    cp.conflict = ch.conflict
                    cp.children = ch.children
                    cp
                }
            }
        }?.toMutableList()
        return p
    }

    fun deepCopy(): TemplateNode {
        return JSON.parseObject(JSON.toJSONString(this), TemplateNode::class.java)
    }

    override fun resolve(context: Map<String, Any?>): PathNode {
        super.resolve(context)
        this.tag = ""
        return this
    }
}
