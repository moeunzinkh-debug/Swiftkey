package hooman.morphe.patches.simsimi.support

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal data class InvokeRedirect(
    val definingClass: String,
    val name: String,
    val parameters: List<String>,
    val returnType: String,
    val replacementDescriptor: String,
    val replacementName: String,
    val replacementParameters: List<String>? = null,
) {
    fun matches(reference: MethodReference): Boolean =
        reference.definingClass == definingClass &&
            reference.name == name &&
            reference.returnType == returnType &&
            reference.parameterTypes.let { actual ->
                actual.size == parameters.size &&
                    actual.zip(parameters).all { (a, b) -> a.toString() == b }
            }

    val replacementSmaliDescriptor: String
        get() = "$replacementDescriptor->$replacementName(" +
            "${(replacementParameters ?: parameters).joinToString("")})$returnType"
}

private val REDIRECTABLE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_STATIC,
    Opcode.INVOKE_STATIC_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
)

private const val EXTENSION_PACKAGE_PREFIX = "Lapp/morphe/extension/"

context(patchContext: BytecodePatchContext)
internal fun redirectInvokes(vararg redirects: InvokeRedirect): Int {
    var totalReplaced = 0

    patchContext.classDefForEach { classDef: ClassDef ->
        if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) return@classDefForEach

        val indicesToReplace = mutableMapOf<Method, List<Pair<Int, InvokeRedirect>>>()

        classDef.methods.forEach { method: Method ->
            val implementation = method.implementation ?: return@forEach
            val hits = mutableListOf<Pair<Int, InvokeRedirect>>()

            implementation.instructions.forEachIndexed { index, instruction ->
                if (instruction.opcode !in REDIRECTABLE_OPCODES) return@forEachIndexed
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@forEachIndexed
                val redirect = redirects.firstOrNull { it.matches(reference) } ?: return@forEachIndexed
                hits += index to redirect
            }

            if (hits.isNotEmpty()) {
                indicesToReplace[method] = hits
            }
        }

        if (indicesToReplace.isEmpty()) return@classDefForEach

        val mutableClass = patchContext.mutableClassDefBy(classDef)

        indicesToReplace.forEach { (method, hits) ->
            val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                candidate.name == method.name &&
                    candidate.returnType == method.returnType &&
                    candidate.parameterTypes.size == method.parameterTypes.size &&
                    candidate.parameterTypes.zip(method.parameterTypes).all { (a, b) -> a.toString() == b.toString() }
            } ?: return@forEach

            hits.sortedByDescending { it.first }.forEach { (index, redirect) ->
                val original = mutableMethod.implementation!!.instructions.toList()[index]
                val replacement = when (original) {
                    is Instruction35c -> {
                        val registers = buildList {
                            add(original.registerC)
                            add(original.registerD)
                            add(original.registerE)
                            add(original.registerF)
                            add(original.registerG)
                        }.take(original.registerCount).joinToString(", ") { "v$it" }
                        "invoke-static { $registers }, ${redirect.replacementSmaliDescriptor}"
                    }
                    is Instruction3rc -> {
                        val start = original.startRegister
                        val end = start + original.registerCount - 1
                        "invoke-static/range { v$start .. v$end }, ${redirect.replacementSmaliDescriptor}"
                    }
                    else -> error(
                        "SimSimi support: unexpected invoke instruction format for " +
                            "${redirect.definingClass}->${redirect.name}: ${original.javaClass.name}",
                    )
                }
                mutableMethod.replaceInstruction(index, replacement)
                totalReplaced++
            }
        }
    }

    return totalReplaced
}
