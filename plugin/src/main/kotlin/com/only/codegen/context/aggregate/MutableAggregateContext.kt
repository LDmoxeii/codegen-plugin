package com.only.codegen.context.aggregate

import com.only.codegen.context.aggregate.models.AggregateInfo

interface MutableAggregateContext : AggregateContext {
    override val aggregateMap: MutableMap<String, AggregateInfo>
}
