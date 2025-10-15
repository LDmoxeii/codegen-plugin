package com.only4.codegen.manager

class SpecificationImportManager: BaseImportManager() {

    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.Specification")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.Specification.Result")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("org.springframework.stereotype.Service")
    }

}
