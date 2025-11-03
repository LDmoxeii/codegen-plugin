package com.only4.codegen.engine.output

import com.only4.codegen.misc.resolvePackageDirectory
import java.io.File
import java.nio.charset.Charset

class FileOutputManager(
    private val moduleBaseDir: String,
    private val encoding: String = "UTF-8",
) : IOutputManager {

    private val generated = mutableListOf<String>()

    override fun write(result: GenerationResult) {
        val dir = createDirectoryStructure(result.packageName ?: "")
        val file = File(dir, result.fileName)
        file.parentFile?.mkdirs()
        file.writeText(result.content, Charset.forName(encoding))
        generated.add(file.absolutePath)
    }

    override fun createDirectoryStructure(packageName: String): String {
        val dir = resolvePackageDirectory(moduleBaseDir, packageName)
        val f = File(dir)
        if (!f.exists()) f.mkdirs()
        return dir
    }

    override fun cleanOutput() {
        // no-op for minimal loop
    }

    override fun getGeneratedFiles(): List<String> = generated.toList()
}

