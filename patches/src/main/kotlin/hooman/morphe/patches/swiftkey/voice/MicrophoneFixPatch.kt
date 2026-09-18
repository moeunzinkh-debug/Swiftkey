package hooman.morphe.patches.swiftkey.voice

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import hooman.morphe.patches.swiftkey.support.InvokeRedirect
import hooman.morphe.patches.swiftkey.support.redirectInvokes
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import hooman.morphe.patches.swiftkey.toolbar.application
import hooman.morphe.patches.swiftkey.toolbar.keyboardToolbarPatch
import hooman.morphe.patches.swiftkey.toolbar.metadata

private const val EXTENSION = "Lapp/morphe/extension/swiftkey/MicSupport;"
private const val CONTEXT = "Landroid/content/Context;"
private const val COMPONENT = "Landroid/content/ComponentName;"
private const val MANAGER = "Landroid/content/pm/PackageManager;"
private const val INFO = "Landroid/content/pm/PackageInfo;"
private const val FLAGS = "Landroid/content/pm/PackageManager\$PackageInfoFlags;"
private const val RECOGNIZER = "Landroid/speech/SpeechRecognizer;"
private const val STRING = "Ljava/lang/String;"

/** Flag for the "Patches" settings entry (PatchesSettings reads it at runtime). */
private val microphoneFixFlagPatch = resourcePatch {
    compatibleWith(swiftKeyCompatibility)
    execute {
        document("AndroidManifest.xml").use { manifest ->
            manifest.documentElement.application()
                .metadata("app.morphe.swiftkey.PATCH_microphone_fix", "true")
        }
    }
}

@Suppress("unused")
val fixMicrophoneVoiceInputPatch = bytecodePatch(
    name = "Offline microphone",
    description = "Embeds a whisper.cpp offline speech engine and adds an Offline mic panel inside the keyboard. " +
        "Choose Khmer, English, Thai or Chinese; tap Download model once (142 MiB shared multilingual model), " +
        "then dictate without internet. No Google app or external speech provider required. " +
        "Also redirects SwiftKey's Android SpeechRecognizer path; Microsoft Azure voice typing is not modified.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(keyboardToolbarPatch, offlineVoiceResourcesPatch, microphoneFixFlagPatch)

    execute {
        // The new toolbar mic works independently. Redirect the stock Android speech path where
        // it exists too; builds using Azure exclusively can still use our offline toolbar button.
        redirectInvokes(
            InvokeRedirect(RECOGNIZER, "isRecognitionAvailable", listOf(CONTEXT), "Z", EXTENSION, "isRecognitionAvailable"),
            InvokeRedirect(RECOGNIZER, "createSpeechRecognizer", listOf(CONTEXT), RECOGNIZER, EXTENSION, "createSpeechRecognizer"),
            InvokeRedirect(RECOGNIZER, "createSpeechRecognizer", listOf(CONTEXT, COMPONENT), RECOGNIZER, EXTENSION, "createSpeechRecognizer"),
            // An invoke-virtual receiver must become the FIRST argument of the static bridge.
            InvokeRedirect(MANAGER, "getPackageInfo", listOf(STRING, "I"), INFO, EXTENSION, "getPackageInfo",
                replacementParameters = listOf(MANAGER, STRING, "I")),
            InvokeRedirect(MANAGER, "getPackageInfo", listOf(STRING, FLAGS), INFO, EXTENSION, "getPackageInfo",
                replacementParameters = listOf(MANAGER, STRING, "Ljava/lang/Object;")),
        )
    }
}
