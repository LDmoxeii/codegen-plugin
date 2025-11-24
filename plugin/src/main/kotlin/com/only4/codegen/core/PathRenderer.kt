package com.only4.codegen.core

import com.only4.codegen.misc.resolvePackage
import com.only4.codegen.template.PathNode
import com.only4.codegen.template.Template
import com.only4.codegen.template.TemplateNode
import java.io.File
import java.nio.charset.Charset

/**
 * Rendering helper that applies PathNode instructions and delegates IO to FileWriter.
 * Keeps rendering logic independent from Gradle task classes.
 */
class PathRenderer(
    private val fileWriter: FileWriter,
    private val basePackageProvider: () -> String,
    private val outputEncodingProvider: () -> String,
    private val templateProvider: () -> Template,
    private val templatePackage: MutableMap<String, String>,
    private val templateParentPath: MutableMap<String, String>,
    private val patternSplitter: Regex = Regex("[,;]"),
    private val protectFlag: String? = null,
) {

    var renderFileSwitch: Boolean = true

    fun forceRender(pathNode: PathNode, parentPath: String): String =
        renderFileSwitch.let { originalValue ->
            renderFileSwitch = true
            try {
                render(pathNode, parentPath)
            } finally {
                renderFileSwitch = originalValue
            }
        }

    fun render(pathNode: PathNode, parentPath: String): String =
        when (pathNode.type?.lowercase()) {
            "root" -> {
                pathNode.children?.forEach { render(it, parentPath) }
                parentPath
            }

            "dir" -> {
                val dirPath = renderDir(pathNode, parentPath)
                pathNode.children?.forEach { render(it, dirPath) }
                dirPath
            }

            "file" -> renderFile(pathNode, parentPath)
            else -> parentPath
        }

    fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        val basePkg = basePackageProvider()
        templateNodes.forEach { templateNode ->
            val tag = requireNotNull(templateNode.tag) { "template tag must not be null" }
            templatePackage[tag] = resolvePackage("${parentPath}${File.separator}X.kt")
                .substring(basePkg.length + 1)
            templateParentPath[tag] = parentPath
        }
    }

    fun renderDir(pathNode: PathNode, parentPath: String): String {
        require(pathNode.type.equals("dir", ignoreCase = true)) { "pathNode must be a directory type" }

        val name = pathNode.name?.takeIf { it.isNotBlank() } ?: return parentPath
        val path = "$parentPath${File.separator}$name"
        fileWriter.ensureDirectory(path, pathNode.conflict)

        pathNode.tag?.takeIf { it.isNotBlank() }?.let { tag ->
            tag.split(patternSplitter)
                .filter { it.isNotBlank() }
                .forEach { renderTemplate(templateProvider().select(it), path) }
        }

        return path
    }

    fun renderFile(pathNode: PathNode, parentPath: String): String {
        require(pathNode.type.equals("file", ignoreCase = true)) { "pathNode must be a file type" }

        val name = pathNode.name?.takeIf { it.isNotBlank() }
            ?: error("pathNode name must not be blank")

        val path = "$parentPath${File.separator}$name"
        if (!renderFileSwitch) return path

        val content = pathNode.data.orEmpty()
        val encoding = pathNode.encoding ?: outputEncodingProvider()
        val charset = Charset.forName(encoding)

        fileWriter.writeFile(path, content, charset, pathNode.conflict, protectFlag)
        return path
    }
}
