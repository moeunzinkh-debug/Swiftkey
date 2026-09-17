package hooman.morphe.patches.swiftkey.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.mutable.MutableMethod
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import hooman.morphe.patches.swiftkey.toolbar.application
import hooman.morphe.patches.swiftkey.toolbar.children
import hooman.morphe.patches.swiftkey.toolbar.metadata
import org.w3c.dom.Element

private const val EXTENSION = "Lapp/morphe/extension/swiftkey/PatchesSettings;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val BUNDLE = "Landroid/os/Bundle;"
private const val ACTION_MAIN = "android.intent.action.MAIN"
private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
private const val FLAG_SETTINGS_UI = "app.morphe.swiftkey.PATCH_settings_ui"

internal var launcherActivityDescriptors: Set<String> = emptySet()

/**
 * Finds the launcher activity — the "SwiftKey settings" screen the app icon opens — from the
 * manifest contract, so no obfuscated class name is guessed. Stamps the flag that the settings
 * entry reports for this build.
 */
internal val patchesSettingsManifestPatch = resourcePatch {
    execute {
        launcherActivityDescriptors = emptySet()
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val originalPackage = packageMetadata.packageName

            fun resolve(name: String): String = when {
                name.isBlank() -> ""
                name.startsWith('.') -> originalPackage + name
                '.' !in name -> "$originalPackage.$name"
                else -> name
            }

            fun isLauncher(component: Element): Boolean = component.children("intent-filter").any { filter ->
                val hasLauncherCategory = filter.children("category")
                    .any { it.getAttribute("android:name") == CATEGORY_LAUNCHER }
                val actions = filter.children("action")
                val hasMainAction = actions.isEmpty() ||
                    actions.any { it.getAttribute("android:name") == ACTION_MAIN }
                hasLauncherCategory && hasMainAction
            }

            val names = linkedSetOf<String>()
            root.application().children("activity").filter { isLauncher(it) }.forEach {
                names += resolve(it.getAttribute("android:name"))
            }
            root.application().children("activity-alias").filter { isLauncher(it) }.forEach {
                names += resolve(it.getAttribute("targetActivity"))
            }

            launcherActivityDescriptors = names
                .filter { it.isNotBlank() }
                .map { "L${it.replace('.', '/')};" }
                .toSet()
            if (launcherActivityDescriptors.isEmpty()) {
                throw PatchException(
                    "SwiftKey Patches: no launcher activity found in the manifest. The settings " +
                        "screen layout changed for this version; the Patches entry cannot be located.",
                )
            }

            root.application().metadata(FLAG_SETTINGS_UI, "true")

            // The Patches settings screen (applied patches list + offline mic settings).
            val activityClass = "app.morphe.extension.swiftkey.PatchesActivity"
            val application = root.application()
            val hasActivity = application.children("activity")
                .any { it.getAttribute("android:name") == activityClass }
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
            throw PatchException(
                "SwiftKey Patches: the launcher activity is missing from the dex, so the Patches " +
                    "settings entry has nowhere to attach. The app layout changed for this version.",
            )
        }
        println("[PatchesSettings][SwiftKey] Hooked $hooked launcher activit${if (hooked == 1) "y" else "ies"}")
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
        BuilderInstruction35c(
            Opcode.INVOKE_STATIC,
            1,
            0,
            0,
            0,
            0,
            ImmutableMethodReference(EXTENSION, "onActivityCreate", listOf(ACTIVITY), "V"),
        ),
    )
}
