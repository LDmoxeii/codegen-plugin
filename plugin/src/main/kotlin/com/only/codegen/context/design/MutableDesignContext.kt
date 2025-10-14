package com.only.codegen.context.design

import com.only.codegen.context.design.models.AggregateInfo
import com.only.codegen.context.design.models.BaseDesign
import com.only.codegen.context.design.models.DesignElement

interface MutableDesignContext : DesignContext {

    override val designElementMap: MutableMap<String, MutableList<DesignElement>>
    override val aggregateMap: MutableMap<String, AggregateInfo>
    override val designMap: MutableMap<String, MutableList<BaseDesign>>
}
