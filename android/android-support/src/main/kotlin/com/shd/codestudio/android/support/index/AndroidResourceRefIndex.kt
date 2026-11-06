package com.shd.codestudio.android.support.index

import com.shd.codestudio.android.support.navigation.ResourceOccurrences
import com.shd.codestudio.index.IndexExtension
import com.shd.codestudio.index.IndexId
import com.shd.codestudio.index.IndexInput
import com.shd.codestudio.index.IndexOrigin
import com.shd.codestudio.index.InputFilter
import com.shd.codestudio.index.KeyDescriptor
import com.shd.codestudio.index.MatchingMode
import com.shd.codestudio.index.StringExternalizer
import com.shd.codestudio.index.StringKeyDescriptor

/**
 * The reverse of [AndroidResourceIndex]: which project files NAME a resource. Keyed like it (`"string/app_name"`,
 * see [ResourceOccurrences.key]), valued by the naming file's path, one entry per file. Fed every project `res/`
 * XML and every `.java`/`.kt` source, so Usages, rename and the layout ↔ class "Related" links read only the
 * files that matter instead of sweeping the project. The ranges themselves are re-found in those files' live
 * text: the index answers "where to look", the text answers "exactly where".
 *
 * Besides resources it carries two lookups the "Related" links need: `"binding/<Class>"` for a code file using
 * a layout's generated binding class, and `"context/<SimpleName>"` for a layout whose `tools:context` names a
 * class of that simple name (the namespace a relative `.Foo` resolves against isn't known here; the reader
 * confirms the full name).
 */
object AndroidResourceRefIndex : IndexExtension<String, String> {
    override val id = IndexId("android.resourceRefs")
    override val version = 1
    override val keyDescriptor: KeyDescriptor<String> = StringKeyDescriptor
    override val valueExternalizer = StringExternalizer
    override val matching = MatchingMode.PREFIX_ONLY

    /** The key layouts whose `tools:context` names a class called [simpleName] are indexed under. */
    fun contextKey(simpleName: String): String = "context/$simpleName"

    private val TOOLS_CONTEXT = Regex("""tools:context\s*=\s*"([^"]+)"""")

    override val inputFilter = InputFilter { input ->
        input.origin == IndexOrigin.SOURCE && (isCode(input) || isResourceXml(input))
    }

    override fun index(input: IndexInput): Map<String, Collection<String>> {
        val path = input.sourcePath ?: return emptyMap()
        val text = input.text() ?: return emptyMap()
        val keys = LinkedHashSet<String>()
        if (isCode(input)) {
            ResourceOccurrences.allInCode(text).mapTo(keys) { it.key }
        } else {
            val folder = path.replace('\\', '/').substringBeforeLast('/', "").substringAfterLast('/')
            ResourceOccurrences.allInXml(text, isValuesFile = folder.substringBefore('-') == "values").mapTo(keys) { it.key }
            TOOLS_CONTEXT.findAll(text).forEach { keys += contextKey(it.groupValues[1].substringAfterLast('.')) }
        }
        val value = listOf(path)
        return keys.associateWith { value }
    }

    private fun isCode(input: IndexInput): Boolean {
        val n = input.unitName ?: input.sourcePath ?: return false
        return n.endsWith(".kt") || n.endsWith(".java")
    }

    private fun isResourceXml(input: IndexInput): Boolean =
        input.sourcePath?.replace('\\', '/')?.let { it.contains("/res/") && it.endsWith(".xml") } == true
}
