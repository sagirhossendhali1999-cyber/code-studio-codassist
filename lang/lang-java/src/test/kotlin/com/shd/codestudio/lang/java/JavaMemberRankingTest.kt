package com.shd.codestudio.lang.java

import com.shd.codestudio.lang.completion.CompletionItem
import com.shd.codestudio.lang.completion.CompletionRelevance
import com.shd.codestudio.lang.completion.CompletionRequest
import com.shd.codestudio.lang.completion.CompletionTrigger
import com.shd.codestudio.lang.completion.complete
import com.shd.codestudio.lang.incremental.DocumentSnapshot
import com.shd.codestudio.lang.java.env.JavaEnvironment
import com.shd.codestudio.testkit.TestDocument
import com.shd.codestudio.vfs.VirtualFile
import com.shd.codestudio.vfs.local.LocalFileSystem
import com.shd.codestudio.vfs.local.fileFor
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The order of a `receiver.` member list: the members code uses most first, then the receiver's own members,
 * then each superclass's nearest first, `Object`'s last, alphabetical within a class. It used to follow the
 * class file's declaration order, so `TextView a; a.` opened on `setAutoSizeTextTypeWithDefaults` and put
 * `setText` 180th.
 */
class JavaMemberRankingTest {
    private lateinit var env: JavaEnvironment
    private lateinit var srcRoot: File
    private lateinit var analyzer: JavaSourceAnalyzer
    private lateinit var fs: LocalFileSystem

    @BeforeTest fun setUp() {
        srcRoot = Files.createTempDirectory("java-rank").toFile()
        File(srcRoot, "com/foo").mkdirs()
        File(srcRoot, "com/foo/Base.java").writeText(
            "package com.foo;\npublic class Base { public void zeta() {} public void alpha() {} }"
        )
        File(srcRoot, "com/foo/Middle.java").writeText(
            "package com.foo;\npublic class Middle extends Base { public void yak() {} public void bee() {} }"
        )
        File(srcRoot, "com/foo/Leaf.java").writeText(
            "package com.foo;\npublic class Leaf extends Middle { public void mid() {} public void ant() {} }"
        )
        env = JavaEnvironment.create(emptyList(), listOf(srcRoot), File(System.getProperty("java.home")))
        analyzer = JavaSourceAnalyzer(env)
        fs = LocalFileSystem(srcRoot.toPath())
    }

    @AfterTest fun tearDown() { env.close(); srcRoot.deleteRecursively() }

    private class Snap(file: VirtualFile, text: CharSequence) : DocumentSnapshot by TestDocument(text, file, 1)

    /** The backend's items in the order the engine's weighers put them: popularity, the callable group, then
     *  how far up the receiver's supertypes each is declared, keeping emission order for ties. */
    private fun rankedLabels(source: String): List<String> = runBlocking {
        val offset = source.indexOf('|')
        val text = source.removeRange(offset, offset + 1)
        val vf = fs.fileFor(File(srcRoot, "com/foo/Use.java").toPath())
        analyzer.complete(CompletionRequest(Snap(vf, text), offset, CompletionTrigger.Explicit), JavaLanguageBackend.LANGUAGE_ID)
            .items
            .sortedWith(
                compareByDescending<CompletionItem> { it.rel.popularity }
                    .thenBy { it.rel.callableWeight }
                    .thenBy { it.rel.receiverDistance }
            )
            .map { it.label }
            .distinct()
    }

    private val CompletionItem.rel get() = relevance ?: CompletionRelevance.NONE

    @Test
    fun theMostUsedStringMembersComeFirst() {
        val labels = rankedLabels("package com.foo;\nclass U { void m(String s) { s.| } }")
        assertEquals(listOf("length", "equals", "substring", "contains", "isEmpty"), labels.take(5))
    }

    @Test
    fun ownMembersPrecedeInheritedOnesNearestFirstAlphabeticallyWithinEach() {
        val labels = rankedLabels("package com.foo;\nclass U { void m(Leaf l) { l.| } }")
        val ours = labels.filter { it in setOf("ant", "mid", "bee", "yak", "alpha", "zeta") }
        assertEquals(listOf("ant", "mid", "bee", "yak", "alpha", "zeta"), ours)
        // Object's methods come after every member the hierarchy declares.
        assertTrue(labels.indexOf("hashCode") > labels.indexOf("zeta"), "Object methods must sink; got $labels")
    }
}
