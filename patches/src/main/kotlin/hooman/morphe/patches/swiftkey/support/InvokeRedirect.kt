package hooman.morphe.patches.swiftkey.support

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * ការ​ប្ដូរ​ការ​ហៅ​មេតូដ​មួយ (invoke-*) ទៅ​កាន់​មេតូដ static ក្នុង extension។
 *
 * ការ​ឆ្លាស់​រក្សា​បញ្ជី register ដដែល​ទាំងស្រុង (1:1) ដូច្នេះ​មិន​ត្រូវការ
 * register ទំនេរ ហើយ​ប្រើ​បាន​ជាមួយ​មេតូដ static និង virtual/interface៖
 *  - invoke-static (គ្មាន this)៖ បញ្ជី​អាគុយម៉ង់​ដូច​គ្នា​ពិតៗ
 *  - invoke-virtual/interface (មាន this ជា​អាគុយម៉ង់​ទីមួយ)៖ មេតូដ static
 *    ជំនួស​ត្រូវ​ប្រកាស​ប៉ារ៉ាម៉ែត្រ​ទីមួយ​ជា​ប្រភេទ​អ្នក​កាន់ this (មើល​ឧ. MicSupport#getPackageInfo)
 *
 * @param definingClass ប្រភេទ​អ្នក​កាន់​មេតូដ​ដើម​ទម្រង់ smali ("Landroid/...;")
 * @param name ឈ្មោះ​មេតូដ​ដើម
 * @param parameters បញ្ជី​ប្រភេទ​ប៉ារ៉ាម៉ែត្រ​ទម្រង់ smali
 * @param returnType ប្រភេទ​តម្លៃ​ត្រឡប់
 * @param replacementDescriptor ប្រភេទ​អ្នក​កាន់​មេតូដ​ជំនួស ("Lapp/morphe/extension/...;")
 * @param replacementName ឈ្មោះ​មេតូដ​ជំនួស (ហត្ថលេខា​ត្រូវ​ទទួល​បញ្ជី register ដូច​គ្នា —
 *        ប៉ុន្តែ​ប្រភេទ​អាច​ធំ​ជាង​បាន ដូច​ជា​ប្រកាស Object ជំនួស​ប្រភេទ API ថ្មី)
 * @param replacementParameters បញ្ជី​ប្រភេទ​ប៉ារ៉ាម៉ែត្រ​របស់​មេតូដ​ជំនួស (លំនាំដើម = [parameters])
 */
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

/**
 * ស្កេន​គ្រប់​ classes*.dex របស់​កម្មវិធី​ (មិន​ប៉ះ​កូដ extension ផ្ទាល់ខ្លួន ដើម្បី​ការពារ
 * ការ​ហៅ​ខ្លួនឯង​ជា​រង្វង់​ដូច​ខ្សែ​សង្វាក់) ហើយ​ប្ដូរ​រាល់​ invoke ដែល​ផ្គូផ្គង
 * [redirects] ទៅជា invoke-static ទៅ​កាន់​ extension។
 *
 * @return ចំនួន​កន្លែង​ហៅ​សរុប​ដែល​ត្រូវ​បាន​ប្ដូរ (សម្រាប់​កំណត់ហេតុ/ការ​ត្រួតពិនិត្យ)
 */
context(patchContext: BytecodePatchContext)
internal fun redirectInvokes(vararg redirects: InvokeRedirect): Int {
    var totalReplaced = 0

    patchContext.classDefForEach { classDef: ClassDef ->
        // មិន​ត្រូវ​កែ​កូដ​ដែល​បាន​ចាក់​បញ្ចូល (extension) — មេតូដ​ជំនួស​ខ្លួន​ឯង​ហៅ​ API
        // ដើម​ទៀត ការ​កែ​វា​នឹង​បង្កើត​រង្វិល​ជុំ​គ្មាន​ទី​បញ្ចប់។
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

            // ប្ដូរ​ពី​ក្រោយ​ទៅ​មុខ ដើម្បី​កុំ​ឲ្យ​លេខ​រៀង​ខ្លួន​មុនៗ​រើ​ទីតាំង។
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
                        "SwiftKey support: unexpected invoke instruction format for " +
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
