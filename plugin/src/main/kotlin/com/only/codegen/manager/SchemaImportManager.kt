package com.only.codegen.manager

class SchemaImportManager: ImportManager {

    override val requiredImports = mutableSetOf<String>()
    override fun addBaseImports() {
        requiredImports.add("org.springframework.data.jpa.domain.Specification")
        requiredImports.add("jakarta.persistence.criteria.*")
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
}
