package hooman.morphe.patches.swiftkey.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import hooman.morphe.patches.swiftkey.toolbar.application
import hooman.morphe.patches.swiftkey.toolbar.attr
import hooman.morphe.patches.swiftkey.toolbar.children
import hooman.morphe.patches.swiftkey.toolbar.descendants
import hooman.morphe.patches.swiftkey.toolbar.flagDefaultsTrue
import hooman.morphe.patches.swiftkey.toolbar.metadata
import org.w3c.dom.Element

private const val EXTENSION = "Lapp/morphe/extension/swiftkey/PatchesSettings;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val BUNDLE = "Landroid/os/Bundle;"
private const val ACTION_MAIN = "android.intent.action.MAIN"
private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
private const val FLAG_SETTINGS_UI = "app.morphe.swiftkey.PATCH_settings_ui"

/** How many components the skip diagnostic prints before trailing off with an ellipsis. */
private const val DIAGNOSTIC_LIMIT = 12

internal var launcherActivityDescriptors: Set<String> = emptySet()

/**
 * One `<activity>` or `<activity-alias>` of the decoded manifest, reduced to everything the
 * launcher decision needs. [className] is already fully qualified, or "" when the component
 * declares no usable name.
 */
internal class LauncherCandidate(
    val tag: String,
    val className: String,
    val actions: Set<String>,
    val categories: Set<String>,
    val enabled: Boolean,
    val alias: Boolean,
) {
    val hasMain: Boolean get() = ACTION_MAIN in actions
    val hasLauncherCategory: Boolean get() = CATEGORY_LAUNCHER in categories
}

/**
 * Ranks the manifest components and returns the best launcher candidates — the "SwiftKey
 * settings" screen the app icon opens.
 *
 * The tiers are ordered from the exact Android launcher contract down to progressively looser
 * readings of the same manifest. An earlier tier that matches anything wins outright, so a
 * normal manifest always behaves exactly like the strict check used to. The loose tiers exist
 * because a real build whose alias carries `android:targetActivity` instead of a bare
 * `targetActivity`, whose launcher entry is `enabled="false"`, whose document uses a remapped
 * namespace prefix, or which exposes no LAUNCHER category at all must still resolve — one
 * unmatched string used to fail the whole patch run.
 *
 * @return the winning candidates, or an empty list when no component could be a launcher.
 */
internal fun rankLauncherCandidates(components: List<LauncherCandidate>): List<LauncherCandidate> {
    // A component the platform will never start cannot host the settings row, so `enabled=false`
    // entries drop out before the tiers are consulted rather than being hooked pointlessly.
    val named = components.filter { it.className.isNotBlank() && it.enabled }

    // Tier 1 — the Android launcher contract proper. Actions and categories are unioned across
    // every intent-filter of the component, so MAIN and LAUNCHER declared in separate filters
    // still match, as does a filter that declares no action at all.
    val strict = named.filter { it.hasLauncherCategory && it.hasMain }
    if (strict.isNotEmpty()) return strict

    // Tier 2 — only half the contract is present: a LAUNCHER category with no MAIN action, or a
    // MAIN action with no LAUNCHER category. Either is still this app's entry point.
    val partial = named.filter { it.hasLauncherCategory || it.hasMain }
    if (partial.isNotEmpty()) return partial

    // Tier 3 — no launcher contract at all. Fall back to a single entry point, preferring an
    // activity-alias (a keyboard app redirects its icon through one) and breaking ties by name
    // so the choice is reproducible. Hooking exactly one keeps the settings row off unrelated
    // activities.
    return named.sortedWith(
        compareByDescending<LauncherCandidate> { it.alias }
            .thenBy { it.className },
    ).take(1)
}

/** Turns `android:name` / `android:targetActivity` into a fully qualified class name. */
private fun resolveComponentName(rawName: String, packageName: String): String = when {
    rawName.isBlank() -> ""
    rawName.startsWith('.') -> packageName + rawName
    '.' !in rawName -> "$packageName.$rawName"
    else -> rawName
}

/** Reads every `<activity>` and `<activity-alias>` of the application subtree. */
private fun collectComponents(application: Element, packageName: String): List<LauncherCandidate> {
    fun read(tag: String, nameAttribute: String) = application.descendants(tag).map { element ->
        val filters = element.children("intent-filter")
        LauncherCandidate(
            tag = tag,
            className = resolveComponentName(attr(element, nameAttribute), packageName),
            actions = filters.flatMap { filter ->
                filter.children("action").map { attr(it, "name") }
            }.filter { it.isNotBlank() }.toSet(),
            categories = filters.flatMap { filter ->
                filter.children("category").map { attr(it, "name") }
            }.filter { it.isNotBlank() }.toSet(),
            enabled = flagDefaultsTrue(element, "enabled"),
            // An alias redirects to another activity, so the target is what must be hooked.
            alias = nameAttribute == "targetActivity",
        )
    }

    // `targetActivity` is read both bare and prefixed: the attribute is not namespaced in a
    // source manifest, but a decoder that normalises it must not silently yield "" and drop the
    // component — which is exactly how an alias launcher used to disappear.
    return read("activity", "name") +
        read("activity-alias", "targetActivity").let { aliases ->
            if (aliases.any { it.className.isNotBlank() }) {
                aliases
            } else {
                read("activity-alias", "name")
            }
        }
}

/** The component dump printed when nothing could be resolved, so the next report is actionable. */
private fun describeComponents(components: List<LauncherCandidate>): String = buildString {
    append(components.size).append(" component(s)")
    components.take(DIAGNOSTIC_LIMIT).forEach { component ->
        append("\n  <").append(component.tag)
        if (component.alias) append("-alias")
        append("> ").append(component.className.ifBlank { "<no android:name>" })
        append(" | enabled=").append(component.enabled)
        append(" | actions=").append(component.actions.ifEmpty { setOf("<none>") })
        append(" | categories=").append(component.categories.ifEmpty { setOf("<none>") })
    }
    if (components.size > DIAGNOSTIC_LIMIT) {
        append("\n  ... ").append(components.size - DIAGNOSTIC_LIMIT).append(" more")
    }
}

/**
 * Discovers the launcher activity — the "SwiftKey settings" screen the app icon opens — from the
 * manifest contract, so no obfuscated class name is guessed. Stamps the flag that the settings
 * entry reports for this build, and always declares the Patches screen so the keyboard toolbar's
 * microphone icon can open it even when the settings row cannot be hooked.
 *
 * Discovery degrades instead of throwing: the settings row is cosmetic, so an unrecognised
 * manifest must never take the keyboard patches down with it.
 */
internal val patchesSettingsManifestPatch = resourcePatch {
    execute {
        launcherActivityDescriptors = emptySet()
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val application = root.application()
            val originalPackage = packageMetadata.packageName
            val components = collectComponents(application, originalPackage)
            val launcherComponents = rankLauncherCandidates(components)

            if (launcherComponents.isEmpty()) {
                println(
                    "[PatchesSettings][SwiftKey] WARNING: no launcher activity resolved; the settings " +
                        "screen entry is skipped and the rest of the build continues. " +
                        "Scanned ${describeComponents(components)}",
                )
            } else {
                launcherActivityDescriptors = launcherComponents
                    .map { "L${it.className.replace('.', '/')};" }
                    .toSet()
                println(
                    "[PatchesSettings][SwiftKey] Launcher candidate(s): " +
                        "${launcherComponents.joinToString { it.className }}",
                )
            }

            // Stamped and declared whether or not a hook target was found: the offline-voice
            // toolbar ships a microphone icon that opens this screen directly.
            application.metadata(FLAG_SETTINGS_UI, "true")

            // The Patches settings screen (applied patches list + offline mic settings).
            val activityClass = "app.morphe.extension.swiftkey.PatchesActivity"
            val hasActivity = application.children("activity")
                .any { attr(it, "name") == activityClass }
            if (!hasActivity) {
                val element = manifest.createElement("activity")
                element.setAttribute("android:name", activityClass)
                element.setAttribute("android:exported", "false")
                element.setAttribute("android:label", "Patches")
                element.setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                application.appendChild(element)
            }
        }
    }
}

@Suppress("unused")
val patchesSettingsPatch = bytecodePatch(
    name = "Patches",
    description = "Adds a Patches entry to the SwiftKey settings screen. Opens the Patches settings " +
        "screen: the patches applied to this build (only the selected ones), plus the offline " +
        "microphone settings (default/installed engines, offline mode toggle, engine switch and " +
        "the K\u00F5nele offline engine installer).",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(patchesSettingsManifestPatch)
    extendWith("extensions/swiftkey.mpe")

    execute {
        var hooked = 0
        launcherActivityDescriptors.forEach { type ->
            if (classDefByOrNull(type) == null) return@forEach
            hookActivity(type)
            hooked++
        }
        if (hooked == 0) {
            // Degrade, do not abort: nothing else in this build depends on the settings row, and
            // the Patches screen stays reachable from the keyboard toolbar's microphone icon.
            println(
                "[PatchesSettings][SwiftKey] WARNING: no launcher activity present in the dex " +
                    "(scanned ${launcherActivityDescriptors.size} manifest candidate(s)); the " +
                    "settings screen entry is skipped. The rest of the build continues.",
            )
        } else {
            println(
                "[PatchesSettings][SwiftKey] Hooked $hooked launcher " +
                    "activit${if (hooked == 1) "y" else "ies"}",
            )
        }
    }
}

/**
 * Hooks onCreate of the given activity: either inject the extension call into the existing
 * implementation or add a thin override that delegates to super. Both keep the original behavior
 * and use no extra registers (p0 is always `this` in an instance method), so high-register
 * methods are safe.
 */
context(patchContext: BytecodePatchContext)
private fun hookActivity(type: String) {
    val classDef = patchContext.classDefBy(type)
    val mutable = patchContext.mutableClassDefBy(classDef)

    fun matches(method: Method) = method.name == "onCreate" &&
        method.returnType == "V" &&
        method.parameterTypes.size == 1 &&
        method.parameterTypes[0].toString() == BUNDLE &&
        !AccessFlags.STATIC.isSet(method.accessFlags)

    val declared = mutable.methods.firstOrNull { matches(it) }
    if (declared == null) {
        val superclass = classDef.superclass
            ?: throw PatchException("SwiftKey Patches: no superclass for $type.")
        val method = MutableMethod(ImmutableMethod(
            type, "onCreate",
            listOf(ImmutableMethodParameter(BUNDLE, emptySet(), null)),
            "V", AccessFlags.PUBLIC.value, emptySet(), emptySet(),
            ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList()),
        ))
        method.addInstructions(
            0,
            """
                invoke-super {p0, p1}, $superclass->onCreate($BUNDLE)V
                invoke-static {p0}, $EXTENSION->onActivityCreate($ACTIVITY)V
                return-void
            """.trimIndent(),
        )
        mutable.methods.add(method)
        return
    }

    val implementation = declared.implementation
        ?: throw PatchException("SwiftKey Patches: $type->onCreate has no implementation in this build.")
    val mutableImplementation = implementation as? MutableMethodImplementation
        ?: throw PatchException("SwiftKey Patches: $type->onCreate is not a mutable implementation.")
    mutableImplementation.addInstruction(
        0,
        // p0 is `this` in an instance method; register fields beyond A are unused.
        BuilderInstruction35c(
            Opcode.INVOKE_STATIC,
            1,
            0,
            0,
            0,
            0,
            0,
            ImmutableMethodReference(EXTENSION, "onActivityCreate", listOf(ACTIVITY), "V"),
        ),
    )
}
