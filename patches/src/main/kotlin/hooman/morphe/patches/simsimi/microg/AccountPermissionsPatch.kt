package hooman.morphe.patches.simsimi.microg

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import hooman.morphe.patches.simsimi.simsimiCompatibility
import hooman.morphe.patches.simsimi.support.simsimiSupportManifestPatch

private const val EXTENSION = "Lapp/morphe/extension/simsimi/GmsSupport;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val BUNDLE = "Landroid/os/Bundle;"

private val LIBRARY_PREFIXES = arrayOf(
    "Landroid/",
    "Landroidx/",
    "Lcom/google/",
    "Lkotlin/",
    "Lkotlinx/",
    "Lorg/",
    "Ljava/",
    "Ldalvik/",
    "Lapp/morphe/",
)

private fun parameterRegisterCount(method: Method): Int {
    val parameterRegisters = method.parameterTypes.sumOf { parameterType ->
        when (parameterType.toString()) {
            "J", "D" -> 2
            else -> 1
        }
    }
    val instanceRegister = if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    return parameterRegisters + instanceRegister
}

@Suppress("unused")
val microgAccountPermissionsPatch = bytecodePatch(
    name = "MicroG Account Permissions",
    description = "Request GmsCore account permissions at runtime once (GET_ACCOUNTS and " +
        "`app.revanced.gms.EXTENDED_ACCESS`) the first time any app activity starts, using a " +
        "structural Activity-superclass walk (R8-name independent). No-op when no GmsCore is installed.",
) {
    compatibleWith(simsimiCompatibility)
    dependsOn(simsimiSupportManifestPatch)
    extendWith("extensions/simsimi.mpe")

    execute {
        fun isApplicationClass(type: String): Boolean =
            LIBRARY_PREFIXES.none { type.startsWith(it) }

        fun extendsActivity(type: String): Boolean {
            var current: String? = type
            val seen = HashSet<String>()
            repeat(24) {
                val cls = current ?: return false
                if (!seen.add(cls)) return false
                if (cls == ACTIVITY) return true
                current = classDefByOrNull(cls)?.superclass
            }
            return false
        }

        val ensureAccountPermissions = ImmutableMethodReference(
            EXTENSION,
            "ensureAccountPermissions",
            listOf(ACTIVITY),
            "V",
        )

        var hookedActivities = 0

        classDefForEach { classDef ->
            val type = classDef.type
            if (!isApplicationClass(type)) return@classDefForEach
            if (!extendsActivity(type)) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            val onCreate = mutableClass.methods.firstOrNull { method ->
                method.name == "onCreate" &&
                    method.returnType == "V" &&
                    method.parameterTypes.let { it.size == 1 && it[0].toString() == BUNDLE } &&
                    method.implementation != null
            } ?: return@classDefForEach

            val implementation = onCreate.implementation as? MutableMethodImplementation
                ?: return@classDefForEach
            val parameterRegisters = parameterRegisterCount(onCreate)
            val registerCount = implementation.registerCount
            if (registerCount < parameterRegisters) {
                println(
                    "[MicroGAccountPermissions][SimSimi] Skipping $type->onCreate(Bundle): " +
                        "registerCount=$registerCount < parameterRegisters=$parameterRegisters",
                )
                return@classDefForEach
            }

            val thisRegister = registerCount - parameterRegisters
            // Avoid InlineSmaliCompiler here: on some SimSimi/Morphe combinations the one-line
            // `invoke-static {p0}, ...` compilation aborts with "Collection is empty." even though
            // the target method is otherwise hookable. Building the invoke directly is equivalent
            // bytecode-wise and sidesteps the brittle smali-template path entirely.
            implementation.addInstruction(
                0,
                if (thisRegister <= 15) {
                    BuilderInstruction35c(
                        Opcode.INVOKE_STATIC,
                        1,
                        thisRegister,
                        0,
                        0,
                        0,
                        0,
                        ensureAccountPermissions,
                    )
                } else {
                    BuilderInstruction3rc(
                        Opcode.INVOKE_STATIC_RANGE,
                        thisRegister,
                        1,
                        ensureAccountPermissions,
                    )
                },
            )
            hookedActivities++
        }

        if (hookedActivities == 0) {
            throw PatchException(
                "SimSimi microG: no application Activity with onCreate(Bundle) was found to hook. " +
                    "The class layout changed; re-derive the permission injection point.",
            )
        }

        println("[MicroGAccountPermissions][SimSimi] Hooked $hookedActivities application activities")
    }
}
