package hooman.morphe.patches.swiftkey.editing

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import hooman.morphe.patches.swiftkey.toolbar.application
import hooman.morphe.patches.swiftkey.toolbar.keyboardToolbarPatch
import hooman.morphe.patches.swiftkey.toolbar.metadata

private val textEditingResourcesPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            manifest.documentElement.application().metadata("app.morphe.swiftkey.TEXT_EDITING", "true")
            // Flag for the "Patches" settings entry (PatchesSettings reads it at runtime).
            manifest.documentElement.application().metadata("app.morphe.swiftkey.PATCH_text_editing", "true")
        }
    }
}

@Suppress("unused")
val textEditingToolbarPatch = bytecodePatch(
    name = "Text editing toolbar",
    description = "Adds a collapsible text editing toolbar inside the keyboard: cursor arrows, Home/End, " +
        "selection, Select all, Cut, Copy, Paste, Undo and Redo. Actions use the current editor; " +
        "Undo/Redo depend on that app's support. Does not change login, telemetry or Google services.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(keyboardToolbarPatch, textEditingResourcesPatch)
}
