package com.shd.codestudio.core.event

import com.shd.codestudio.analysis.AnalysisEvent
import com.shd.codestudio.analysis.AnalysisEventListener
import com.shd.codestudio.analysis.AnalysisTopics
import com.shd.codestudio.build.BuildEvent
import com.shd.codestudio.build.BuildEventListener
import com.shd.codestudio.build.BuildTopics
import com.shd.codestudio.build.RunEvent
import com.shd.codestudio.build.RunEventListener
import com.shd.codestudio.index.IndexEvent
import com.shd.codestudio.index.IndexEventListener
import com.shd.codestudio.index.IndexStatus
import com.shd.codestudio.index.IndexTopics
import com.shd.codestudio.model.event.ProjectEvent
import com.shd.codestudio.model.event.ProjectEventListener
import com.shd.codestudio.model.event.ProjectTopics
import com.shd.codestudio.platform.impl.MessageBusImpl
import com.shd.codestudio.plugin.editor.EditorEvent
import com.shd.codestudio.plugin.editor.EditorEventListener
import com.shd.codestudio.plugin.editor.EditorTopics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Round-trips each plugin-facing lifecycle topic over a real bus: the sealed payloads and the
 *  `fun interface` listeners must proxy correctly (this is what the engine/backend publish sites drive).
 *  The topics themselves live in the api module that owns each payload, so a plugin outside this build can
 *  name them; this module is their only publisher. */
class LifecycleTopicsTest {

    @Test
    fun `editor topic delivers events in order`() {
        val bus = MessageBusImpl()
        val got = mutableListOf<EditorEvent>()
        bus.connect().subscribe(EditorTopics.EDITOR, EditorEventListener { got.add(it) })

        val pub = bus.syncPublisher(EditorTopics.EDITOR)
        pub.onEditorEvent(EditorEvent.FileOpened("/a.kt"))
        pub.onEditorEvent(EditorEvent.SelectionChanged("/a.kt", 3, 7))
        pub.onEditorEvent(EditorEvent.ActiveEditorChanged(null))

        assertEquals(
            listOf(
                EditorEvent.FileOpened("/a.kt"),
                EditorEvent.SelectionChanged("/a.kt", 3, 7),
                EditorEvent.ActiveEditorChanged(null),
            ),
            got,
        )
    }

    @Test
    fun `build run project indexing analysis topics deliver their payloads`() {
        val bus = MessageBusImpl()
        val builds = mutableListOf<BuildEvent>()
        val runs = mutableListOf<RunEvent>()
        val projects = mutableListOf<ProjectEvent>()
        val indexing = mutableListOf<IndexEvent>()
        val analysis = mutableListOf<AnalysisEvent>()
        bus.connect().also { c ->
            c.subscribe(BuildTopics.BUILD, BuildEventListener { builds.add(it) })
            c.subscribe(BuildTopics.RUN, RunEventListener { runs.add(it) })
            c.subscribe(ProjectTopics.LIFECYCLE, ProjectEventListener { projects.add(it) })
            c.subscribe(IndexTopics.INDEXING, IndexEventListener { indexing.add(it) })
            c.subscribe(AnalysisTopics.ANALYSIS, AnalysisEventListener { analysis.add(it) })
        }

        bus.syncPublisher(BuildTopics.BUILD).onBuildEvent(BuildEvent.Started("app", listOf("compileJava", "jar")))
        bus.syncPublisher(BuildTopics.BUILD).onBuildEvent(BuildEvent.Finished("app", succeeded = false, failureKind = "compile", message = "boom"))
        bus.syncPublisher(BuildTopics.RUN).onRunEvent(RunEvent.Started("app", "Main"))
        bus.syncPublisher(BuildTopics.RUN).onRunEvent(RunEvent.Finished("app", exitCode = 0, succeeded = true))
        bus.syncPublisher(ProjectTopics.LIFECYCLE).onProjectEvent(ProjectEvent.Opened("/proj"))
        bus.syncPublisher(IndexTopics.INDEXING).onIndexEvent(IndexEvent.Started)
        bus.syncPublisher(IndexTopics.INDEXING).onIndexEvent(IndexEvent.Finished(IndexStatus(building = false, ready = true)))
        bus.syncPublisher(AnalysisTopics.ANALYSIS).onAnalysisEvent(AnalysisEvent("/a.kt", emptyList()))

        assertEquals(BuildEvent.Started("app", listOf("compileJava", "jar")), builds[0])
        assertEquals(BuildEvent.Finished("app", succeeded = false, failureKind = "compile", message = "boom"), builds[1])
        assertEquals(listOf(RunEvent.Started("app", "Main"), RunEvent.Finished("app", 0, true)), runs)
        assertEquals(ProjectEvent.Opened("/proj"), projects.single())
        assertEquals<IndexEvent>(IndexEvent.Started, indexing[0])
        assertTrue((indexing[1] as IndexEvent.Finished).status.ready)
        assertEquals(AnalysisEvent("/a.kt", emptyList()), analysis.single())
    }

    /**
     * The bus keys subscriptions by [com.shd.codestudio.platform.Topic.name], so the string is the wire identity, not
     * the constant. The constants now live in five modules that version independently of the IDE, and a
     * plugin built against an older one stays subscribed only while these names hold. Renaming one
     * unsubscribes every existing plugin silently, with no compile error anywhere.
     */
    @Test
    fun `topic names are the published wire identity`() {
        assertEquals("ide.editor", EditorTopics.EDITOR.name)
        assertEquals("ide.build", BuildTopics.BUILD.name)
        assertEquals("ide.run", BuildTopics.RUN.name)
        assertEquals("ide.analysis", AnalysisTopics.ANALYSIS.name)
        assertEquals("ide.project", ProjectTopics.LIFECYCLE.name)
        assertEquals("ide.indexing", IndexTopics.INDEXING.name)
    }
}
