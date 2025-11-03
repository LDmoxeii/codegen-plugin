package com.only4.codegen.engine.generation.common

import com.only4.codegen.context.BaseContext
import com.only4.codegen.misc.refPackage

object V2ModelBuilder {

    /**
     * Build a Pebble model map for v2 generation using legacy-compatible keys.
     * - Merges DesignContext.baseMap
     * - Adds templatePackage/package (as refPackage), Comment, templateBaseDir
     * - Adds imports if provided
     * - Adds arbitrary variables in vars (e.g., "Query" -> "FindUserQry")
     */
    fun model(
        context: BaseContext,
        templateBaseDir: String,
        templatePackageRaw: String,
        packageRaw: String,
        comment: String,
        vars: Map<String, Any?> = emptyMap(),
        imports: List<String> = emptyList(),
        extras: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val model = context.baseMap.toMutableMap()
        model["templatePackage"] = refPackage(templatePackageRaw)
        model["package"] = refPackage(packageRaw)
        model["Comment"] = comment
        model["templateBaseDir"] = templateBaseDir
        if (imports.isNotEmpty()) model["imports"] = imports
        vars.forEach { (k, v) -> model[k] = v }
        extras.forEach { (k, v) -> model[k] = v }
        return model
    }
}
