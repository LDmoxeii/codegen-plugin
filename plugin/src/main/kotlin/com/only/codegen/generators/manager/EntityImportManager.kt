package com.only.codegen.generators.manager

/**
 * 实体类 Import 管理器，用于智能管理实体类的 import 语句
 */
class EntityImportManager : ImportManager {
    private val requiredImports = mutableSetOf<String>()

    /**
     * 添加实体类基础必需的 imports
     */
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("jakarta.persistence.*")
        requiredImports.add("org.hibernate.annotations.DynamicInsert")
        requiredImports.add("org.hibernate.annotations.DynamicUpdate")
    }

    /**
     * 根据条件添加 imports
     * @param condition 是否需要添加
     * @param imports 要添加的 import 列表
     */
    override fun addIfNeeded(condition: Boolean, vararg imports: String) {
        if (condition) {
            requiredImports.addAll(imports)
        }
    }

    /**
     * 直接添加 import
     * @param imports 要添加的 import 列表
     */
    override fun add(vararg imports: String) {
        requiredImports.addAll(imports)
    }

    /**
     * 获取格式化的 import 列表，自动添加空行分隔
     * @return 格式化后的 import 行列表（包含 "import " 前缀和空行）
     */
    override fun toImportLines(): List<String> {
        val sorted = requiredImports.sorted()
        val result = mutableListOf<String>()

        // 添加空行作为开始（package 和 import 之间的空行）
        result.add("")

        var lastPackageGroup: String? = null
        sorted.forEach { importStr ->
            // 判断当前包属于哪个组
            val currentGroup = when {
                importStr.startsWith("jakarta") -> "jakarta"
                importStr.startsWith("org.hibernate") -> "hibernate"
                else -> "other"
            }

            // 在不同组之间添加空行分隔
            if (lastPackageGroup != null && lastPackageGroup != currentGroup) {
                result.add("")
            }

            // 添加 "import " 前缀
            result.add("import $importStr")
            lastPackageGroup = currentGroup
        }

        return result
    }

    /**
     * 检查是否已包含指定的 import
     * @param importStr 要检查的 import
     * @return 是否已包含
     */
    override fun contains(importStr: String): Boolean {
        return requiredImports.contains(importStr)
    }

    /**
     * 获取当前所有的 imports
     * @return 所有 import 的集合
     */
    override fun getImports(): Set<String> {
        return requiredImports.toSet()
    }
}
