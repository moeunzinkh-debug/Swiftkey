package hooman.morphe.patches.swiftkey.microg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.swiftkey.support.swiftKeySupportManifestPatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility

private const val EXTENSION = "Lapp/morphe/extension/swiftkey/GmsSupport;"
private const val ACTIVITY = "Landroid/app/Activity;"
private const val BUNDLE = "Landroid/os/Bundle;"

/**
 * បុព្វបទ​កញ្ចប់​ប្រព័ន្ធ/បណ្ណាល័យ ដែល​មិន​ត្រូវ​ចាត់ទុក​ជា Activity "របស់​កម្មវិធី"
 * (មិន​ចាំបាច់ និង​មិន​គួរ​បន្ថែម​ស្នាម​របស់​យើង​ទៅ​ក្នុង​កូដ​បណ្ណាល័យ)។
 */
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

/**
 * MicroG Account Permissions
 *
 * នៅ​ពេល​ប្រើ GmsCore ការ​ចូល​គណនី Google តម្រូវ​ឲ្យ​ភាគី​កម្មវិធី​កាន់កាប់​សិទ្ធិ
 * android.permission.GET_ACCOUNTS និង​សិទ្ធិ​ពិសេស app.revanced.gms.EXTENDED_ACCESS
 * (GmsCore RE ប្រកាស​វា​ជា​សិទ្ធិ "dangerous" ដែល​ត្រូវ​ស្នើ​នៅ​ពេល​រត់)។
 * SwiftKey​មិន​ដែល​ស្នើ​សិទ្ធិ​ទាំងនេះ (សារពើភ័ណ្ឌ​សិទ្ធិ​របស់​វា​មិន​ឆ្លុះ​បញ្ចាំង
 * ការ​រត់​លើ GmsCore) ដូច្នេះ​ការ​ចូល​គណនី​បរាជ័យ​ដោយ​ស្ងាត់។
 *
 * បំណះ​ស្កេន​រចនាសម្ព័ន្ធ​រាល់​ Activity របស់​កម្មវិធី (ដើរ​តាម​ខ្សែ​ស្នង​មរតក
 * superclass រហូត​ដល់ android.app.Activity មិន​ពឹង​ឈ្មោះ R8) ហើយ​បញ្ចូល​ការ​ហៅ​
 * GmsSupport.ensureAccountPermissions(this) នៅ​ដើម onCreate(Bundle)។
 * មេតូដ​ផ្នែក extension ធានា​ថា​ប្រអប់​សុំ​សិទ្ធិ​លេច​ម្ដងគត់​ក្នុង​មួយ​ដំណើរការ
 * ហើយ​លោត​ចេញ​ដោយ​ស្ងាត់​ពេល​មិនមាន GmsCore។
 */
@Suppress("unused")
val microgAccountPermissionsPatch = bytecodePatch(
    name = "MicroG Account Permissions",
    description = "Requests the GmsCore account permissions (GET_ACCOUNTS and " +
        "app.revanced.gms.EXTENDED_ACCESS) at runtime the first time a SwiftKey activity starts, " +
        "so account access works against MicroG RE without an ADB helper. Does nothing when no " +
        "GmsCore is installed.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(swiftKeySupportManifestPatch)
    extendWith("extensions/swiftkey.mpe")

    execute {
        fun isApplicationClass(type: String): Boolean =
            LIBRARY_PREFIXES.none { type.startsWith(it) }

        // ដើរ​ខ្សែ​ស្នងមរតក​រហូត​ដល់ android.app.Activity (កំណត់​ជម្រៅ​ការពារ​ខ្សែ​រង្វិល)។
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
                    method.parameterTypes.let { it.size == 1 && it[0].toString() == BUNDLE }
            } ?: return@classDefForEach

            // p0 = this (Activity)។ ស្នាម​បន្ថែម​មិន​ប្រើ​ register បន្ថែម​ទេ
            // ដូច្នេះ​មិន​ប៉ះពាល់​ការ​បម្រុង​ទុក register របស់​មេតូដ​ដើម។
            onCreate.addInstructions(
                0,
                "invoke-static {p0}, $EXTENSION->ensureAccountPermissions(Landroid/app/Activity;)V",
            )
            hookedActivities++
        }

        if (hookedActivities == 0) {
            throw PatchException(
                "SwiftKey microG: no application Activity with onCreate(Bundle) was found to hook. " +
                    "The class layout changed; re-derive the permission injection point.",
            )
        }

        println("[MicroGAccountPermissions] Hooked $hookedActivities application activities")
    }
}
