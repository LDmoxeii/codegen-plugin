package com.only4.codegen.engine.validation

/**
 * 简化的元数据验证接口。
 */
interface IMetadataValidator<E, EN, R> {
    fun validateEntity(entity: E): ValidationResult
    fun validateEnum(enumDef: EN): ValidationResult
    fun validateRelationship(relationship: R): ValidationResult
}

