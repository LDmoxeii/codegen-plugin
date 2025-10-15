package com.only4.codegen.context.design.builders

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.only4.codegen.context.ContextBuilder
import com.only4.codegen.context.design.MutableDesignContext
import com.only4.codegen.context.design.models.AggregateInfo
import com.only4.codegen.ksp.models.AggregateMetadata
import java.io.File

class KspMetadataContextBuilder(
    private val metadataPath: String,
) : ContextBuilder<MutableDesignContext> {

    override val order: Int = 15

    private val gson = Gson()

    override fun build(context: MutableDesignContext) {
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
        context: MutableDesignContext,
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
