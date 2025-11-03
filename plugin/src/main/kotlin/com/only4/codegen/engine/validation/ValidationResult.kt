package com.only4.codegen.engine.validation

data class ValidationResult(
    val isValid: Boolean = true,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

