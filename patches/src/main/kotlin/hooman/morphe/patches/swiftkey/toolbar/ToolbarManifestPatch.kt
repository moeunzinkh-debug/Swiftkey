package hooman.morphe.patches.swiftkey.toolbar

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

internal var imeServiceDescriptors: Set<String> = emptySet()

internal fun Element.children(tag: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

internal fun Element.application(): Element = children("application").singleOrNull()
    ?: throw PatchException("SwiftKey: expected exactly one application in AndroidManifest.xml.")

internal fun Element.metadata(name: String, value: String) {
    val item = children("meta-data").firstOrNull { it.getAttribute("android:name") == name }
        ?: ownerDocument.createElement("meta-data").also { appendChild(it) }
    item.setAttribute("android:name", name)
    item.setAttribute("android:value", value)
}

/** Discover the real IME from its manifest contract, not a guessed/obfuscated class name. */
internal val toolbarManifestPatch = resourcePatch {
    execute {
        imeServiceDescriptors = emptySet()
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val sdk = root.children("uses-sdk").firstOrNull()
                ?: manifest.createElement("uses-sdk").also { root.appendChild(it) }
            val declaredMin = sdk.getAttribute("android:minSdkVersion")
            val minSdk = if (declaredMin.isBlank()) 1 else declaredMin.toIntOrNull() ?: 26
            if (minSdk < 26) sdk.setAttribute("android:minSdkVersion", "26")
            val originalPackage = packageMetadata.packageName
            imeServiceDescriptors = root.application().children("service")
                .filter { it.getAttribute("android:permission") == "android.permission.BIND_INPUT_METHOD" }
                .map { service ->
                    val name = service.getAttribute("android:name")
                    if (name.isBlank()) throw PatchException("SwiftKey: input method service has no class name.")
                    val qualified = when {
                        name.startsWith('.') -> originalPackage + name
                        '.' !in name -> "$originalPackage.$name"
                        else -> name
                    }
                    "L${qualified.replace('.', '/')};"
                }.toSet()
            if (imeServiceDescriptors.isEmpty()) {
                throw PatchException("SwiftKey: no BIND_INPUT_METHOD service found. Select the full keyboard APK.")
            }
        }
    }
}
