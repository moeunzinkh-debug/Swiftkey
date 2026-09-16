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
            if (parent != IME) {
                println("[KeyboardToolbar] Note: $type is an indirect InputMethodService descendant.")
            }

            try {
                wrapLifecycle(
                    type, "onCreateInputView", emptyList(), VIEW,
                    after = """
                        move-result-object v0
                        invoke-static {p0, v0}, $TOOLBAR->wrap($IME$VIEW)$VIEW
                        move-result-object v0
                        return-object v0
                    """.trimIndent(),
                )
            } catch (e: Exception) {
                println("[KeyboardToolbar] Warning: onCreateInputView wrapping failed on $type: ${e.message}")
            }

            val reset = "invoke-static/range {p0 .. p0}, $TOOLBAR->resetForInput($IME)V"
            try {
                wrapLifecycle(type, "onStartInput", listOf("Landroid/view/inputmethod/EditorInfo;", "Z"), "V", before = reset)
            } catch (e: Exception) {
                println("[KeyboardToolbar] Warning: onStartInput wrapping skipped on $type: ${e.message}")
            }
            try {
                wrapLifecycle(type, "onFinishInputView", listOf("Z"), "V", before = reset)
            } catch (e: Exception) {
                println("[KeyboardToolbar] Warning: onFinishInputView wrapping skipped on $type: ${e.message}")
            }
            try {
                wrapLifecycle(type, "onWindowHidden", emptyList(), "V", before = reset)
            } catch (e: Exception) {
                println("[KeyboardToolbar] Warning: onWindowHidden wrapping skipped on $type: ${e.message}")
            }
            try {
                wrapLifecycle(
                    type, "onComputeInsets", listOf(INSETS), "V",
                    after = """
                        invoke-static {p0, p1}, $TOOLBAR->includeToolsInInsets($IME$INSETS)V
                        return-void
                    """.trimIndent(),
                )
            } catch (e: Exception) {
                println("[KeyboardToolbar] Warning: onComputeInsets wrapping skipped on $type: ${e.message}")
            }
        }
    }
}

/**
 * Use a safe wrapper with dedicated registers instead of inserting a multi-register invoke into an
 * arbitrary method. This preserves all original branches/returns and protects against register collisions.
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

    val classDef = patchContext.classDefByOrNull(type) ?: run {
        println("[KeyboardToolbar] Class not found: $type")
        return
    }
    val declared = classDef.methods.firstOrNull { matches(it) }
    if (declared != null && (declared.implementation == null || AccessFlags.STATIC.isSet(declared.accessFlags))) {
        println("[KeyboardToolbar] $type->$name is abstract or static; skipping.")
        return
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
                    return
                }
                foundInputViewImplementation = true
                if (AccessFlags.FINAL.isSet(inherited.accessFlags)) {
                    println("[KeyboardToolbar] $name is final in $ancestor; skipping to protect APK.")
                    return
                }
                break
            }
            ancestor = definition.superclass
        }
        if (name == "onCreateInputView" && !foundInputViewImplementation) {
            println("[KeyboardToolbar] onCreateInputView not found in ancestry of $type; skipping.")
            return
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
            println("[KeyboardToolbar] $type already has $bridge, skipping to avoid double wrap.")
            return
        }
        mutable.methods.remove(original)
        original.setName(bridge)
        original.setAccessFlags((flags and (AccessFlags.PUBLIC.value or AccessFlags.PROTECTED.value).inv()) or AccessFlags.PRIVATE.value)
        mutable.methods.add(original)
        invocation = "invoke-direct/range {p0 .. p${parameters.size}}, $type->$bridge$signature"
    } else {
        val superclass = classDef.superclass ?: return
        invocation = "invoke-super/range {p0 .. p${parameters.size}}, $superclass->$name$signature"
    }
    val totalRegisters = 4 + 1 + parameters.size
    val wrapper = MutableMethod(
        ImmutableMethod(
            type, name,
            parameters.map { com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter(it, emptySet(), null) },
            returnType, flags, emptySet(), emptySet(),
            ImmutableMethodImplementation(
                totalRegisters,
                emptyList(), emptyList(), emptyList(),
            ),
        ),
    )
    wrapper.addInstructions(0, listOf(before, invocation, after).filter { it.isNotBlank() }.joinToString("\n"))
    mutable.methods.add(wrapper)
}
