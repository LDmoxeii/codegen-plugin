package com.only4.codegen.manager

class SchemaImportManager: BaseImportManager() {

    override fun addBaseImports() {
        requiredImports.add("org.springframework.data.jpa.domain.Specification")
        requiredImports.add("jakarta.persistence.criteria.*")
    }

}
