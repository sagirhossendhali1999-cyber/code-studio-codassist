package com.shd.codestudio.lang.imports

import com.shd.codestudio.lang.incremental.DocumentEdit
import com.shd.codestudio.vfs.VirtualFile

/**
 * "Optimize Imports" SPI: re-emit a file's whole import section in a consistent order. A backend collects its
 * imports, drops the unused ones, and re-renders them through [ImportLayout] (de-duplicated, wildcard-collapsed,
 * sorted, blocks split), returning the minimal [DocumentEdit]s to reach that layout. Empty list = the imports
 * are already optimal (or the buffer can't be safely parsed).
 *
 * Extensible the same way formatting is: a backend advertises [com.shd.codestudio.lang.BackendCapability.ORGANIZE_IMPORTS]
 * and returns an [ImportOrganizerService] from [com.shd.codestudio.lang.SourceAnalyzer.importOrganizer]. Auto-import
 * PLACEMENT (splicing one new import in sorted order) is a separate, per-feature concern handled inside each
 * backend's completion / quick-fix via [ImportLayout.planInsert]; this interface is only the on-demand
 * whole-file command.
 */
interface ImportOrganizerService {
    /** The minimal edits that reorder + de-duplicate + wildcard-collapse + drop-unused the imports of [file],
     *  reading [text] as the current buffer. Empty when already optimal or on an unrecoverable parse. Runs on
     *  the shared engine thread. */
    suspend fun organizeImports(file: VirtualFile, text: CharSequence): List<DocumentEdit>
}
