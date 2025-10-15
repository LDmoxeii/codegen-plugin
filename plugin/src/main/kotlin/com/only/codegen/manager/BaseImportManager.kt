package com.only.codegen.manager

/**
 * Import 管理器的通用实现
 */
abstract class BaseImportManager : ImportManager {
    override val requiredImports = mutableSetOf<String>()

    override fun toImportLines(): List<String> {
        val sorted = requiredImports.sorted()
        val result = mutableListOf<String>()

        result.add("")

        var lastPackageGroup: String? = null
        sorted.forEach { importStr ->
            val currentGroup = when {
                importStr.startsWith("com.only4.cap4k") -> "cap4k"
                importStr.startsWith("org.springframework") -> "spring"
                importStr.startsWith("jakarta") -> "jakarta"
                importStr.startsWith("org.hibernate") -> "hibernate"
                importStr.startsWith("org.slf4j") -> "slf4j"
                else -> "other"
            }

            if (lastPackageGroup != null && lastPackageGroup != currentGroup) {
                result.add("")
            }

            result.add("import $importStr")
            lastPackageGroup = currentGroup
        }

        return result
    }
}
