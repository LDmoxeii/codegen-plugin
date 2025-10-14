package com.only.codegen.context.aggregate.builders

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.only.codegen.context.aggregate.MutableAggregateContext
import com.only.codegen.context.aggregate.models.AggregateInfo
import com.only.codegen.ksp.models.AggregateMetadata
import java.io.File

class KspMetadataContextBuilder(
    private val metadataPath: String,
) : AggregateContextBuilder {

    override val order: Int = 10

    private val gson = Gson()

    override fun build(context: MutableAggregateContext) {
        val aggregatesFile = File(metadataPath, "aggregates.json")
        if (!aggregatesFile.exists()) {
            return
        }

        val aggregates = parseAggregatesMetadata(aggregatesFile)
        processAggregates(aggregates, context)
    }

    private fun parseAggregatesMetadata(file: File): List<AggregateMetadata> {
        val type = object : TypeToken<List<AggregateMetadata>>() {}.type
        return gson.fromJson(file.readText(), type)
    }

    private fun processAggregates(
        aggregates: List<AggregateMetadata>,
        context: MutableAggregateContext,
    ) {
        aggregates.forEach { metadata ->
            val aggregateInfo = AggregateInfo.fromKspMetadata(
                metadata = metadata,
                modulePath = context.domainPath
            )

            context.aggregateMap[metadata.aggregateName] = aggregateInfo
        }
    }
}
