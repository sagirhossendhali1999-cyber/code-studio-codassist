package com.shd.codestudio.lang.xml.folding

import com.shd.codestudio.lang.dom.ParsedFile
import com.shd.codestudio.lang.folding.FoldRegion
import com.shd.codestudio.lang.folding.FoldingService
import com.shd.codestudio.lang.xml.PsiXmlProjection
import com.shd.codestudio.vfs.VirtualFile

/**
 * Code folding for XML: collapses each element's body (`<tag>…</tag>` → `<tag>...</tag>`) and each block
 * comment. Ranges come straight from the PSI (exact `XmlTagValue` / `XmlComment` spans via
 * [PsiXmlProjection.folds]); the editor drops single-line / zero-width regions, so nothing needs pre-filtering.
 *
 * It re-parses the buffer text to PSI (cheap for XML) rather than reconstructing body ranges from the neutral
 * DOM, since PSI exposes the between-tags content range precisely. [parseOf] yields the cached parse so the
 * text matches what the editor displays.
 */
class XmlFoldingService(private val parseOf: suspend (VirtualFile) -> ParsedFile?) :
    FoldingService {

    override suspend fun folds(file: VirtualFile): List<FoldRegion> {
        val text = parseOf(file)?.text() ?: return emptyList()
        return PsiXmlProjection.folds(file.path, text)
    }
}
