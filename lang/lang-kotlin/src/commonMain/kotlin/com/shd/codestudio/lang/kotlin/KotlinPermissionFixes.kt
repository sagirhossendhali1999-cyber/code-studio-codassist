package com.shd.codestudio.lang.kotlin

import com.shd.codestudio.kotlin.syntax.psi.KtBlockExpression
import com.shd.codestudio.kotlin.syntax.psi.KtDeclaration
import com.shd.codestudio.kotlin.syntax.psi.KtElement
import com.shd.codestudio.kotlin.syntax.psi.KtExpression
import com.shd.codestudio.kotlin.syntax.psi.KtFile
import com.shd.codestudio.kotlin.syntax.psi.KtNamedFunction
import com.shd.codestudio.kotlin.syntax.psi.findElementAt
import com.shd.codestudio.lang.incremental.DocumentEdit

/**
 * The missing-permission check's in-file fix, the one Android Studio offers: check the permission before the
 * statement and return when it is not granted. Offered only where that `return` is valid, a statement directly
 * in the body of a function returning `Unit`. (Adding the permission to the manifest is the host's fix.)
 */
internal object KotlinPermissionFixes {

    private const val ACTIVITY_COMPAT = "androidx.core.app.ActivityCompat"
    private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
    private const val MANIFEST = "android.Manifest"
    private const val PLATFORM_PERMISSION = "android.permission."

    fun checkFix(ktFile: KtFile, offset: Int, permissions: List<String>, anyOf: Boolean, hasActivityCompat: Boolean): KotlinImportFix? {
        if (permissions.isEmpty()) return null
        val leaf = ktFile.findElementAt(offset.coerceIn(0, (ktFile.textLength - 1).coerceAtLeast(0))) ?: return null
        val statement = statementInUnitFunction(leaf) ?: return null
        val text = ktFile.text
        val at = statement.textRange.startOffset
        val indent = KotlinApiLevelFixes.indentAt(text, at)
        val check = if (hasActivityCompat) "ActivityCompat.checkSelfPermission(this, " else "checkSelfPermission("
        val condition = permissions.joinToString(if (anyOf) " &&\n$indent    " else " ||\n$indent    ") { p ->
            "$check${constant(p)}) != PackageManager.PERMISSION_GRANTED"
        }
        val edits = ArrayList<DocumentEdit>()
        edits += DocumentEdit(at, 0,
            "if ($condition) {\n" +
                "$indent    // TODO: request the permission with ActivityCompat.requestPermissions and handle the result\n" +
                "$indent    return\n" +
                "$indent}\n$indent")
        val imported = ktFile.importDirectives.mapNotNull { it.importedFqName?.asString() }.toHashSet()
        val imports = buildList {
            if (hasActivityCompat) add(ACTIVITY_COMPAT)
            add(PACKAGE_MANAGER)
            if (permissions.any { it.startsWith(PLATFORM_PERMISSION) }) add(MANIFEST)
        }
        for (fqn in imports) {
            if (fqn in imported) continue
            KotlinImportEdits.planImport(ktFile, fqn)?.let { edits += DocumentEdit(it.offset, 0, it.text) }
        }
        return KotlinImportFix("Add permission check", edits)
    }

    /** `android.permission.CAMERA` as `Manifest.permission.CAMERA`; any other permission as a string. */
    private fun constant(permission: String): String =
        if (permission.startsWith(PLATFORM_PERMISSION)) "Manifest.permission.${permission.removePrefix(PLATFORM_PERMISSION)}"
        else "\"$permission\""

    /** The statement holding [leaf] when it sits directly in the block body of a function returning `Unit`. */
    private fun statementInUnitFunction(leaf: KtElement): KtExpression? {
        var n: KtElement? = leaf
        while (n != null) {
            val parent = n.parent
            if (parent is KtBlockExpression && n is KtExpression) {
                if (n is KtDeclaration) return null
                val function = parent.parent as? KtNamedFunction ?: return null
                val returns = function.typeReference?.text?.trim()
                return n.takeIf { returns == null || returns == "Unit" }
            }
            if (n is KtDeclaration) return null
            n = parent
        }
        return null
    }
}
