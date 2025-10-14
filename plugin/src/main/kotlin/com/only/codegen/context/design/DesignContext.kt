package com.only.codegen.context.design

import com.only.codegen.context.BaseContext
import com.only.codegen.context.design.models.AggregateInfo
import com.only.codegen.context.design.models.BaseDesign
import com.only.codegen.context.design.models.DesignElement

interface DesignContext : BaseContext {

    val designElementMap: Map<String, List<DesignElement>>

    val aggregateMap: Map<String, AggregateInfo>

    val designMap: Map<String, List<BaseDesign>>

    /**
     * 设计标签别名映射表
     * key: 别名（小写）
     * value: 规范化的标签名称
     */
    val designTagAliasMap: Map<String, String>
}
