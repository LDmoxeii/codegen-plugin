package com.only.codegen.pebble

import io.pebbletemplates.pebble.loader.Loader
import java.io.Reader

class CompositeLoader<T>(
    private val primary: Loader<T>,
    private val fallback: Loader<T>
) : Loader<T> {

    override fun getReader(templateName: T): Reader {
        return try {
            primary.getReader(templateName)
        } catch (_: Exception) {
            fallback.getReader(templateName)
        }
    }

    override fun setPrefix(prefix: String?) {
        primary.setPrefix(prefix)
        fallback.setPrefix(prefix)
    }

    override fun setSuffix(suffix: String?) {
        primary.setSuffix(suffix)
        fallback.setSuffix(suffix)
    }

    override fun setCharset(charset: String?) {
        primary.setCharset(charset)
        fallback.setCharset(charset)
    }

    override fun resolveRelativePath(relativePath: String?, anchorPath: String?): String? {
        return primary.resolveRelativePath(relativePath, anchorPath)
    }

    override fun createCacheKey(templateName: String?): T? {
        return primary.createCacheKey(templateName)
    }

    override fun resourceExists(templateName: String?): Boolean {
        return (templateName != null &&
                (primary.resourceExists(templateName) || fallback.resourceExists(templateName)))
    }
}
