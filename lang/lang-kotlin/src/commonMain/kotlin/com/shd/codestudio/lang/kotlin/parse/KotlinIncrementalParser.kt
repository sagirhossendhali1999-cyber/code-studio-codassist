package com.shd.codestudio.lang.kotlin.parse

import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.dom.TextRange
import com.shd.codestudio.lang.incremental.DocumentEdit
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.incremental.IncrementalParser
import com.shd.codestudio.lang.incremental.ReparseResult

/**
 * Parsing strategy: [reparse] builds the new tree from the previous one, parsing only the edited function
 * body or lambda when the edit stays inside one, and the whole file otherwise (see
 * [KotlinParserHost.reparse]). The tree is the one a full parse builds either way.
 *
 * The reparsed range is reported as the whole file: the tree is new, and nothing downstream reads the range.
 */
class KotlinIncrementalParser : IncrementalParser {

    override fun parseFull(snapshot: DocumentSnapshot): ParsedFile {
        val ktFile = KotlinParserHost.parse(snapshot.file.name, snapshot.text)
        return KotlinParsedFile(ktFile, snapshot.file, snapshot.version)
    }

    override fun reparse(
        previous: ParsedFile,
        newSnapshot: DocumentSnapshot,
        edits: List<DocumentEdit>,
    ): ReparseResult {
        val prev = (previous as? KotlinParsedFile)?.ktFile
        val tree = if (prev == null) parseFull(newSnapshot) else KotlinParsedFile(
            KotlinParserHost.reparse(prev, newSnapshot.file.name, newSnapshot.text), newSnapshot.file, newSnapshot.version,
        )
        return ReparseResult(
            tree = tree,
            reparsedRange = TextRange(0, newSnapshot.length()),
            reusedSubtrees = 0,
        )
    }
}
