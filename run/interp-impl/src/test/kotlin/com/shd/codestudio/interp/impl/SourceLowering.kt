package com.shd.codestudio.interp.impl

import com.shd.codestudio.interp.api.LoweredProgram
import com.shd.codestudio.kotlin.syntax.psi.KtNamedFunction
import com.shd.codestudio.lang.kotlin.interp.KotlinTreeResolver
import com.shd.codestudio.lang.kotlin.parse.KotlinParsedFile
import com.shd.codestudio.lang.kotlin.parse.KotlinParserHost
import com.shd.codestudio.lang.kotlin.symbols.KotlinSymbolService
import com.shd.codestudio.testkit.DiskVirtualFile
import com.shd.codestudio.testkit.TestJars
import com.shd.codestudio.testkit.writeSource
import java.nio.file.Files

/**
 * Lower [code] to a [LoweredProgram] the way the host does, so a source session can be tested over REAL
 * Kotlin rather than a hand-built tree.
 *
 * The host's own lowering ([com.shd.codestudio.interp.api.CodeInterpreter.lower]) needs an open project: its analyzers,
 * its module graph and its indexes. This is the same lowering over one file in a temp directory, which is
 * what makes the sessions testable here instead of only in `:ide-core`.
 */
fun loweredProgram(code: String, entry: String): LoweredProgram {
    val dir = Files.createTempDirectory("interp-impl-test")
    dir.writeSource("Prog.kt", code, trim = false)
    val service = KotlinSymbolService(listOf(DiskVirtualFile(dir)), listOf(TestJars.kotlinStdlib().toString()))
    val kt = KotlinParserHost.parse("Prog.kt", code)
    val parsed = KotlinParsedFile(kt, DiskVirtualFile(dir.resolve("Prog.kt")), 0)
    val resolver = KotlinTreeResolver(kt, parsed, service)
    val functions = kt.declarations.filterIsInstance<KtNamedFunction>()
        .map { resolver.lowerFunction(it) }
        .associateBy { "${it.name}/${it.params.size}" }
    val classes = resolver.lowerClasses()
    val fn = functions.entries.firstOrNull { it.key.substringBeforeLast('/') == entry }?.value
    val type = classes.firstOrNull { it.simpleName == entry || it.fqn == entry }
    return LoweredKotlinProgram(
        functions = functions,
        classes = classes,
        entryFunction = if (type == null) requireNotNull(fn) { "no `$entry` in the program" } else null,
        entryType = if (type == null) null else type,
    )
}
