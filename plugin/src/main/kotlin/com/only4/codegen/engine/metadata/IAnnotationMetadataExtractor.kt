package com.only4.codegen.engine.metadata

/**
 * 注解（KSP 等）元数据提取器扩展接口。
 */
interface IAnnotationMetadataExtractor<E, EN, R> : IMetadataExtractor<E, EN, R> {
    fun processSymbols(symbols: Sequence<Any>)
}

