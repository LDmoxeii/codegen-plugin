package com.only.codegen.context.builders

import com.only.codegen.context.MutableEntityContext

class EnumPackageContextBuilder: ContextBuilder {
    override val order: Int = 60

    override fun build(context: MutableEntityContext) {

        val defaultEnumPackage = "enums"

        context.enumConfigMap.keys.forEach { enumType ->
            val tableName = context.enumTableNameMap[enumType]!!

            // 计算枚举包路径
            val enumPackageSuffix = buildString {
                val packageName = context.templateNodeMap["enum"]
                    ?.takeIf { it.isNotEmpty() }
                    ?.get(0)?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultEnumPackage

                if (packageName.isNotBlank()) {
                    append(".$packageName")
                }
            }

            val entityPackage = context.tablePackageMap[tableName]
            val fullEnumPackage = "${context.getString("basePackage")}.$entityPackage$enumPackageSuffix"

            context.enumPackageMap[enumType] = fullEnumPackage

        }
    }
}
