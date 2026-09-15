package hooman.morphe.patches.simsimi.microg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
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

            onCreate.addInstructions(
                0,
                "invoke-static {p0}, $EXTENSION->ensureAccountPermissions(Landroid/app/Activity;)V",
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
