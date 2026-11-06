package com.shd.codestudio.lang.xml

import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.incremental.DocumentEdit
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.incremental.IncrementalParser
import com.shd.codestudio.lang.incremental.ReparseResult

/**
 * XML is parsed in full on every change. Resource/layout files are small (hundreds of lines), the parser
 * is linear and allocation-light, and a full parse keeps recovery simple and correct. The SPI's
 * structural-sharing reparse is therefore implemented as a full reparse for now; the backend does not
 * advertise [com.shd.codestudio.lang.BackendCapability.INCREMENTAL]. The seam is preserved so a future windowed
 * reparse can slot in without touching callers.
 */
class XmlIncrementalParser : IncrementalParser {

    override fun parseFull(snapshot: DocumentSnapshot): ParsedFile {
        val (root, diagnostics) = XmlTreeParser(snapshot).parse()
        return XmlParsedFile(root, snapshot.file, snapshot.version, diagnostics)
    }

    override fun reparse(
        previous: ParsedFile,
        newSnapshot: DocumentSnapshot,
        edits: List<DocumentEdit>,
    ): ReparseResult {
        val tree = parseFull(newSnapshot)
        return ReparseResult(tree, tree.range, reusedSubtrees = 0)
    }
}
