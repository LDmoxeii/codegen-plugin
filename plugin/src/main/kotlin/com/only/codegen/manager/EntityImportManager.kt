package com.only.codegen.manager

class EntityImportManager : ImportManager {
    override val requiredImports = mutableSetOf<String>()

    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("jakarta.persistence.*")
        requiredImports.add("org.hibernate.annotations.DynamicInsert")
        requiredImports.add("org.hibernate.annotations.DynamicUpdate")
    }

    override fun toImportLines(): List<String> {
        val sorted = requiredImports.sorted()
        val result = mutableListOf<String>()

        result.add("")

        var lastPackageGroup: String? = null
        sorted.forEach { importStr ->
            val currentGroup = when {
                importStr.startsWith("jakarta") -> "jakarta"
                importStr.startsWith("org.hibernate") -> "hibernate"
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
