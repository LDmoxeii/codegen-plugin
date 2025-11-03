package com.only4.codegen.engine.metadata

import java.sql.Connection

/**
 * 数据库元数据提取器扩展接口。
 */
interface IDatabaseMetadataExtractor<E, EN, R> : IMetadataExtractor<E, EN, R> {
    fun getConnection(): Connection
}

