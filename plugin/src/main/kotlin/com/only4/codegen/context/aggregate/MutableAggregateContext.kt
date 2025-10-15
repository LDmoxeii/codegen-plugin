package com.only4.codegen.context.aggregate

import com.only4.codegen.template.TemplateNode

interface MutableAggregateContext : AggregateContext {
    override val tableMap: MutableMap<String, Map<String, Any?>>
    override val columnsMap: MutableMap<String, List<Map<String, Any?>>>
    override val tablePackageMap: MutableMap<String, String>
    override val entityTypeMap: MutableMap<String, String>
    override val tableModuleMap: MutableMap<String, String>
    override val tableAggregateMap: MutableMap<String, String>
    override val annotationsMap: MutableMap<String, Map<String, String>>
    override val relationsMap: MutableMap<String, Map<String, String>>

    override val enumConfigMap: MutableMap<String, Map<Int, Array<String>>>

    override val templateNodeMap: MutableMap<String, MutableList<TemplateNode>>
}
