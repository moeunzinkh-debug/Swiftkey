package hooman.morphe.patches.swiftkey.toolbar

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal var imeServiceDescriptors: Set<String> = emptySet()

/** The namespace prefix a decoded manifest normally uses for Android attributes. */
private const val NS = "android"

/** Strips any `prefix:` from a qualified name, leaving the local name. */
private fun localPart(qualified: String?): String = qualified?.substringAfterLast(':').orEmpty()

/**
 * Matches a tag by local name, whatever prefix the decoder wrote.
 *
 * Morphe parses the decoded manifest with a namespace-unaware builder, so `tagName` holds the
 * qualified name as written (`activity`) and `localName` is null. Comparing only against the
 * literal `activity` / `android:activity` means a decoder that emits any other prefix silently
 * matches nothing — which is how a whole patch run used to be lost — so the local name is what
 * gets compared.
 */
internal fun tagMatches(element: Element, tag: String): Boolean = localPart(element.tagName) == tag

private fun NodeList.asElements(): List<Element> =
    (0 until length).mapNotNull { item(it) as? Element }

/** Direct child elements whose tag matches [tag]. */
internal fun Element.children(tag: String): List<Element> =
    childNodes.asElements().filter { tagMatches(it, tag) }

/**
 * Every element in the subtree whose tag matches [tag], not just direct children. Decoders differ
 * in how deeply they nest components, so discovery scans the whole subtree.
 */
internal fun Element.descendants(tag: String): List<Element> =
    childNodes.asElements().flatMap { child ->
        if (tagMatches(child, tag)) listOf(child) + child.descendants(tag) else child.descendants(tag)
    }

/**
 * Reads an attribute by local name, whatever prefix the decoder wrote: `android:name`, a remapped
 * `a:name`, a bare `name`, or a namespace-aware node. Returns "" when absent — never null — so
 * callers can compare against "" for "attribute missing".
 *
 * The exact `android:` spelling is tried first because it is the overwhelmingly common one, then
 * any other prefix, then the bare and namespace-aware forms.
 */
internal fun attr(element: Element, localName: String): String {
    element.getAttribute("$NS:$localName").takeIf { it.isNotEmpty() }?.let { return it }

    val attributes = element.attributes
    for (index in 0 until attributes.length) {
        val node = attributes.item(index) as? Attr ?: continue
        if (localPart(node.name) == localName) {
            node.value.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }

    element.getAttribute(localName).takeIf { it.isNotEmpty() }?.let { return it }
    return element.getAttributeNS("*", localName).orEmpty()
}

/**
 * `android:enabled` and `android:exported` default to true when the attribute is absent, so only
 * the literal string "false" counts as off.
 */
internal fun flagDefaultsTrue(element: Element, localName: String): Boolean =
    attr(element, localName).trim().lowercase() != "false"

internal fun Element.application(): Element = children("application").singleOrNull()
    ?: throw PatchException("SwiftKey: expected exactly one application in AndroidManifest.xml.")

internal fun Element.metadata(name: String, value: String) {
    val item = children("meta-data").firstOrNull { attr(it, "name") == name }
        ?: ownerDocument.createElement("meta-data").also { appendChild(it) }
    item.setAttribute("$NS:name", name)
    item.setAttribute("$NS:value", value)
}

/** Discover the real IME from its manifest contract, not a guessed/obfuscated class name. */
internal val toolbarManifestPatch = resourcePatch {
    execute {
        imeServiceDescriptors = emptySet()
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val sdk = root.children("uses-sdk").firstOrNull()
                ?: manifest.createElement("uses-sdk").also { root.appendChild(it) }
            val declaredMin = attr(sdk, "minSdkVersion")
            val minSdk = if (declaredMin.isBlank()) 1 else declaredMin.toIntOrNull() ?: 26
            if (minSdk < 26) sdk.setAttribute("$NS:minSdkVersion", "26")
            val originalPackage = packageMetadata.packageName
            imeServiceDescriptors = root.application().children("service")
                .filter { attr(it, "permission") == "android.permission.BIND_INPUT_METHOD" }
                .map { service ->
                    val name = attr(service, "name")
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
