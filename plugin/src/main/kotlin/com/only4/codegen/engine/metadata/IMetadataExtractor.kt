package com.only4.codegen.engine.metadata

import com.only4.codegen.engine.validation.ValidationResult

/**
 * 元数据提取器通用接口。
 * 使用类型参数以便实现方选择具体模型类型。
 */
interface IMetadataExtractor<E, EN, R> {
    fun extractEntityMetadata(): List<E>
    fun extractEnumMetadata(): List<EN>
    fun extractRelationshipMetadata(): List<R>
    fun validateMetadata(): ValidationResult
}

