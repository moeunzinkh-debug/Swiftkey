package hooman.morphe.patches.swiftkey.toolbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val IME = "Landroid/inputmethodservice/InputMethodService;"
private const val VIEW = "Landroid/view/View;"
private const val INSETS = "Landroid/inputmethodservice/InputMethodService\$Insets;"
private const val TOOLBAR = "Lapp/morphe/extension/swiftkey/toolbar/KeyboardToolbar;"

/** Shared implementation only. Selecting one feature never silently selects the other. */
internal val keyboardToolbarPatch = bytecodePatch {
    dependsOn(toolbarManifestPatch)
    extendWith("extensions/swiftkey.mpe")

    execute {
        imeServiceDescriptors.forEach { type ->
            var parent: String? = type
            val seen = hashSetOf<String>()
            while (parent != null && parent != IME && seen.add(parent)) {
                parent = classDefByOrNull(parent)?.superclass
            }
            if (parent != IME) throw PatchException("SwiftKey: $type no longer extends InputMethodService.")

            wrapLifecycle(type, "onCreateInputView", emptyList(), VIEW,
                after = """
                    move-result-object v0
                    invoke-static {p0, v0}, $TOOLBAR->wrap($IME$VIEW)$VIEW
                    move-result-object v0
                    return-object v0
                """.trimIndent())

            val reset = "invoke-static/range {p0 .. p0}, $TOOLBAR->resetForInput($IME)V"
            wrapLifecycle(type, "onStartInput", listOf("Landroid/view/inputmethod/EditorInfo;", "Z"), "V", before = reset)
            wrapLifecycle(type, "onFinishInputView", listOf("Z"), "V", before = reset)
            wrapLifecycle(type, "onWindowHidden", emptyList(), "V", before = reset)
            wrapLifecycle(type, "onComputeInsets", listOf(INSETS), "V",
                after = """
                    invoke-static {p0, p1}, $TOOLBAR->includeToolsInInsets($IME$INSETS)V
                    return-void
                """.trimIndent())
        }
    }
}

/**
 * Use a small wrapper with its OWN registers instead of inserting a two-register invoke into an
 * arbitrary method. This preserves all original branches/returns and works with high register IDs.
 * Only the concrete manifest IME is wrapped, so superclass return types/casts are not disturbed.
 */
context(patchContext: BytecodePatchContext)
private fun wrapLifecycle(
    type: String,
    name: String,
    parameters: List<String>,
    returnType: String,
    before: String = "",
    after: String = "return-void",
) {
    fun matches(method: Method) = method.name == name && method.returnType == returnType &&
        method.parameterTypes.map { it.toString() } == parameters

    val classDef = patchContext.classDefBy(type)
    val declared = classDef.methods.firstOrNull { matches(it) }
    if (declared != null && (declared.implementation == null || AccessFlags.STATIC.isSet(declared.accessFlags))) {
        throw PatchException("SwiftKey: $type->$name cannot be wrapped in this APK version.")
    }
    if (declared == null) {
        var foundInputViewImplementation = false
        var ancestor = classDef.superclass
        val seen = hashSetOf<String>()
        while (ancestor != null && ancestor != IME && seen.add(ancestor)) {
            val definition = patchContext.classDefByOrNull(ancestor) ?: break
            val inherited = definition.methods.firstOrNull { matches(it) }
            if (inherited != null) {
                if (inherited.implementation == null || AccessFlags.STATIC.isSet(inherited.accessFlags)) {
                    throw PatchException("SwiftKey: no callable inherited $name implementation in $ancestor.")
                }
                foundInputViewImplementation = true
                if (AccessFlags.FINAL.isSet(inherited.accessFlags)) {
                    throw PatchException("SwiftKey: $name is final in $ancestor; this version needs a new toolbar hook.")
                }
                break
            }
            ancestor = definition.superclass
        }
        if (name == "onCreateInputView" && !foundInputViewImplementation) {
            throw PatchException("SwiftKey: this build creates its view outside onCreateInputView; a new toolbar hook is required.")
        }
    }
    val mutable = patchContext.mutableClassDefBy(classDef)
    val original = mutable.methods.firstOrNull { matches(it) }
    val signature = "(${parameters.joinToString("")})$returnType"
    val flags = original?.accessFlags ?: AccessFlags.PUBLIC.value
    val invocation: String
    if (original != null) {
        val bridge = "morphe\$$name"
        if (mutable.methods.any { it.name == bridge }) {
            throw PatchException("SwiftKey: $type already has $bridge. Patch the original, unmodified APK.")
        }
        // Methods are hash-based collections: remove BEFORE changing the name/signature.
        mutable.methods.remove(original)
        original.setName(bridge)
        original.setAccessFlags((flags and (AccessFlags.PUBLIC.value or AccessFlags.PROTECTED.value).inv()) or AccessFlags.PRIVATE.value)
        mutable.methods.add(original)
        invocation = "invoke-direct/range {p0 .. p${parameters.size}}, $type->$bridge$signature"
    } else {
        val superclass = classDef.superclass ?: throw PatchException("SwiftKey: no superclass for $type.")
        invocation = "invoke-super/range {p0 .. p${parameters.size}}, $superclass->$name$signature"
    }
    val wrapper = MutableMethod(ImmutableMethod(
        type, name,
        parameters.map { com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter(it, emptySet(), null) },
        returnType, flags, emptySet(), emptySet(),
        ImmutableMethodImplementation(1 + parameters.size + (if (returnType == VIEW) 1 else 0),
            emptyList(), emptyList(), emptyList()),
    ))
    wrapper.addInstructions(0, listOf(before, invocation, after).filter { it.isNotBlank() }.joinToString("\n"))
    mutable.methods.add(wrapper)
}
