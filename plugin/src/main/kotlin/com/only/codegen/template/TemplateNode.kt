package com.only.codegen.template

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

    fun deepCopy(): TemplateNode {
        return JSON.parseObject(JSON.toJSONString(this), TemplateNode::class.java)
    }

    override fun resolve(context: Map<String, Any?>): PathNode {
        super.resolve(context)
        this.tag = ""
        return this
    }
}
