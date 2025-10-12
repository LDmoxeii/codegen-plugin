package com.only.codegen.context.entity

import com.only.codegen.template.TemplateNode

interface MutableEntityContext : EntityContext {
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
