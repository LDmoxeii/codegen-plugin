package com.only.codegen.manager

class RepositoryImportManager : ImportManager {
    override val requiredImports = mutableSetOf<String>()

    override fun addBaseImports() {
        requiredImports.add("org.springframework.data.jpa.repository.JpaRepository")
        requiredImports.add("org.springframework.data.jpa.repository.JpaSpecificationExecutor")
        requiredImports.add("org.springframework.stereotype.Repository")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository")
        requiredImports.add("org.springframework.stereotype.Component")
    }

    override fun toImportLines(): List<String> {
        val sorted = requiredImports.sorted()
        val result = mutableListOf<String>()

        result.add("")

        var lastPackageGroup: String? = null
        sorted.forEach { importStr ->
            val currentGroup = when {
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
