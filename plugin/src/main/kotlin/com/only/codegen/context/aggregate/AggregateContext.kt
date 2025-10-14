package com.only.codegen.context.aggregate

import com.only.codegen.context.BaseContext
import com.only.codegen.context.aggregate.models.AggregateInfo

interface AggregateContext : BaseContext {

    val aggregateMap: Map<String, AggregateInfo>
}


