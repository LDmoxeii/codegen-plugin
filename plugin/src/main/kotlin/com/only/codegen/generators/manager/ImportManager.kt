package com.only.codegen.generators.manager

/**
 * Import 管理器接口，用于管理不同类型类文件的 import 语句
 */
interface ImportManager {
    /**
     * 添加基础必需的 imports
     */
    fun addBaseImports()

    /**
     * 根据条件添加 imports
     * @param condition 是否需要添加
     * @param imports 要添加的 import 列表
     */
    fun addIfNeeded(condition: Boolean, vararg imports: String)

    /**
     * 直接添加 import
     * @param imports 要添加的 import 列表
     */
    fun add(vararg imports: String)

    /**
     * 获取格式化的 import 列表
     * @return 格式化后的 import 行列表
     */
    fun toImportLines(): List<String>

    /**
     * 检查是否已包含指定的 import
     * @param importStr 要检查的 import
     * @return 是否已包含
     */
    fun contains(importStr: String): Boolean

    /**
     * 获取当前所有的 imports
     * @return 所有 import 的集合
     */
    fun getImports(): Set<String>
}
