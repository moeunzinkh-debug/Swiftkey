package hooman.morphe.patches.swiftkey.voice

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import hooman.morphe.patches.swiftkey.toolbar.application
import hooman.morphe.patches.swiftkey.toolbar.children
import hooman.morphe.patches.swiftkey.toolbar.metadata
import org.w3c.dom.Element

private object NativeVoiceResources
private const val EXTENSION_PACKAGE = "app.morphe.extension.swiftkey.voice"

internal val offlineVoiceResourcesPatch = resourcePatch {
    execute {
        val loader = NativeVoiceResources::class.java.classLoader
        val libraries = loader.getResourceAsStream("native/swiftkey/index.txt")?.bufferedReader()?.use { it.readLines() }
            ?.filter { it.isNotBlank() }
            ?: throw PatchException("SwiftKey: the bundle has no offline voice native libraries. Rebuild with the Android NDK.")
        val pattern = Regex("lib/(arm64-v8a|armeabi-v7a|x86|x86_64)/libswiftkey_whisper\\.so")
        if (libraries.isEmpty() || libraries.any { !pattern.matches(it) }) {
            throw PatchException("SwiftKey: invalid native voice library index.")
        }
        // Morphe Patcher 1.5.2 fully stages native libraries in FULL resource mode.
        // Keep the APK's existing ABIs. Adding a new ABI could make Android choose an ABI
        // for which SwiftKey's own native engine is missing and break the entire keyboard.
        val targetAbis = get("lib").listFiles()?.filter { directory ->
            directory.isDirectory && directory.listFiles()?.any { it.name.endsWith(".so") } == true
        }?.map { it.name }?.toSet().orEmpty()
        if (targetAbis.isEmpty()) {
            throw PatchException("SwiftKey: no native libraries found. Use a complete APK or merge its ABI splits first.")
        }
        targetAbis.forEach { abi ->
            val path = "lib/$abi/libswiftkey_whisper.so"
            if (path !in libraries) throw PatchException("SwiftKey: offline voice does not support the APK's $abi ABI.")
            val output = get(path)
            if (output.exists()) throw PatchException("SwiftKey: offline voice already exists. Patch an original APK.")
            output.parentFile.mkdirs()
            val stream = loader.getResourceAsStream("native/swiftkey/$path")
                ?: throw PatchException("SwiftKey: missing $path in patch bundle.")
            stream.use { input -> output.outputStream().use { input.copyTo(it) } }
        }

        val notices = get("assets/swiftkey-offline-voice/THIRD_PARTY_NOTICES.txt")
        notices.parentFile.mkdirs()
        val licenseStream = loader.getResourceAsStream("native/swiftkey/THIRD_PARTY_NOTICES.txt")
            ?: throw PatchException("SwiftKey: missing third-party notices in the voice bundle.")
        licenseStream.use { input -> notices.outputStream().use { input.copyTo(it) } }

        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val application = root.application()
            application.metadata("app.morphe.swiftkey.OFFLINE_VOICE", "true")
            // Morphe repacks .so files; installer extraction avoids relying on ZIP entry alignment.
            application.setAttribute("android:extractNativeLibs", "true")
            listOf("android.permission.RECORD_AUDIO", "android.permission.INTERNET").forEach { permission ->
                if (root.children("uses-permission").none { it.getAttribute("android:name") == permission }) {
                    root.appendChild(manifest.createElement("uses-permission").also {
                        it.setAttribute("android:name", permission)
                    })
                }
            }
            fun component(tag: String, name: String): Element = application.children(tag)
                .firstOrNull { it.getAttribute("android:name") == name }
                ?: manifest.createElement(tag).also {
                    it.setAttribute("android:name", name)
                    application.appendChild(it)
                }

            // The toolbar, downloader and recognizer must share a process. Some IMEs use
            // a private :keyboard process rather than the application's default process.
            val defaultProcess = application.getAttribute("android:process")
            val imeProcesses = application.children("service")
                .filter { it.getAttribute("android:permission") == "android.permission.BIND_INPUT_METHOD" }
                .map { it.getAttribute("android:process").ifBlank { defaultProcess } }
            val appPackage = root.getAttribute("package")
            val effectiveProcesses = imeProcesses.map { process ->
                when {
                    process.isBlank() -> appPackage
                    process.startsWith(':') -> appPackage + process
                    else -> process
                }
            }.toSet()
            if (effectiveProcesses.size != 1) {
                throw PatchException("SwiftKey: offline voice requires one IME process; this APK needs a new service integration.")
            }
            val service = component("service", "$EXTENSION_PACKAGE.OfflineRecognitionService")
            imeProcesses.first().takeIf { it.isNotBlank() }?.let { service.setAttribute("android:process", it) }
            service.setAttribute("android:exported", "true")
            service.setAttribute("android:permission", "android.permission.BIND_SPEECH_RECOGNITION_SERVICE")
            service.setAttribute("android:label", "SwiftKey Offline voice")
            val filter = manifest.createElement("intent-filter")
            filter.appendChild(manifest.createElement("action").also {
                it.setAttribute("android:name", "android.speech.RecognitionService")
            })
            service.appendChild(filter)

            val activity = component("activity", "$EXTENSION_PACKAGE.MicrophonePermissionActivity")
            activity.setAttribute("android:exported", "false")
            activity.setAttribute("android:excludeFromRecents", "true")
            activity.setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")

            val queries = root.children("queries").firstOrNull()
                ?: manifest.createElement("queries").also { root.appendChild(it) }
            val speech = "android.speech.RecognitionService"
            if (queries.children("intent").none { intent -> intent.children("action").any { it.getAttribute("android:name") == speech } }) {
                queries.appendChild(manifest.createElement("intent").also { intent ->
                    intent.appendChild(manifest.createElement("action").also { it.setAttribute("android:name", speech) })
                })
            }
        }
    }
}
