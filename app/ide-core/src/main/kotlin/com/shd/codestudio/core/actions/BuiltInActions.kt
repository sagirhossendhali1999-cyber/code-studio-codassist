package com.shd.codestudio.core.actions

import com.shd.codestudio.core.ApplicationEnvironment
import com.shd.codestudio.platform.ExtensionRegistry
import com.shd.codestudio.platform.PluginId
import com.shd.codestudio.plugin.action.ActionPlaces
import com.shd.codestudio.plugin.action.ActionResult
import com.shd.codestudio.plugin.action.SimpleAction
import com.shd.codestudio.plugin.action.UI_ACTION_EP

/**
 * The host's built-in [com.shd.codestudio.plugin.action.IdeAction]s — pure engine commands surfaced through the action
 * registry, the same way a plugin would contribute them. Registered ONCE, app-global, from
 * [ApplicationEnvironment]; each action resolves the open project through [ApplicationEnvironment.activeEngine]
 * at invoke time (it fires outside any service scope), so a single registration serves every opened project.
 *
 * Scope note: only commands that are pure engine operations (no UI navigation / app-state change) live here
 * in Phase A; the navigation-heavy menus (the "More" sheet, the stateful top-bar buttons) move to the action
 * model once the UI-side action registry lands (see `docs/ui-extensibility-and-plugin-api.md`, Phase B).
 */
object BuiltInActions {
    val PLUGIN = PluginId("ide-core-actions")

    fun register(extensions: ExtensionRegistry, env: ApplicationEnvironment) {
        // Command palette: engine commands. These need no UI effect — they act on the engine and report a
        // status message the palette surfaces. The palette renders these through [IdeBackend.actionsFor],
        // replacing the previously-hardcoded entries.
        extensions.register(
            UI_ACTION_EP,
            SimpleAction(
                id = "ide.runBuild",
                text = "Run Build",
                places = setOf(ActionPlaces.COMMAND_PALETTE),
                iconId = "run",
                order = 70,
            ) {
                val s = env.activeEngine ?: return@SimpleAction ActionResult.message("No project is open")
                s.build.runBuild(); ActionResult.message("Build started")
            },
            PLUGIN,
        )
        extensions.register(
            UI_ACTION_EP,
            SimpleAction(
                id = "ide.stopBuild",
                text = "Stop Build",
                places = setOf(ActionPlaces.COMMAND_PALETTE),
                iconId = "stop",
                order = 71,
            ) {
                val s = env.activeEngine ?: return@SimpleAction ActionResult.message("No project is open")
                s.build.stopBuild(); ActionResult.message("Build stopped")
            },
            PLUGIN,
        )
        extensions.register(
            UI_ACTION_EP,
            SimpleAction(
                id = "ide.reindex",
                text = "Re-index Project",
                places = setOf(ActionPlaces.COMMAND_PALETTE),
                iconId = "refresh",
                order = 80,
            ) {
                val s = env.activeEngine ?: return@SimpleAction ActionResult.message("No project is open")
                s.reindex(); ActionResult.message("Re-indexing project…")
            },
            PLUGIN,
        )
    }
}
