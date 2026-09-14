package  hooman.morphe.patches.swiftkey.microg

import app.morphe.patcher.patch.bytecodePatch
import  hooman.morphe.patches.swiftkey.support.InvokeRedirect
import hooman.morphe.patches.swiftkey.support.redirectInvokes
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility

private const val EXTENSION = "Lapp/morphe/extension/swiftkey/GmsSupport;"

/**
 * Process Name Spoofing for Clone Support
 *
 * នៅ​ពេល​កម្មវិធី​ត្រូវ​បាន​ក្លូន (Morphe "Clone app" បន្ថែម​បច្ច័យ ".morphe"
 * ទៅ​កញ្ចប់) ឈ្មោះ​ដំណើរការ​ក៏​ក្លាយ​ទៅ com.touchtype.swiftkey.morphe[:...]។
 * កម្មវិធី​ដែល​ប្រើ​ឈ្មោះ​ដំណើរការ​ដើម្បី​ដោះ​ការ​កំណត់ (switch tables លើ​
 * hashCode ឈ្មោះ, ការ​ប្រៀបធៀប main process, Dagger/component routing)
 * នឹង​វង្វេង​មាគ៌ា — បង្កើត​បាន​ខុស​សមាសភាគ ឬ​គាំង​នៅ​ពេល​ចាប់ផ្តើម។
 *
 * បំណះ​ប្ដូរ​ការ​ហៅ​អ្នក​ផ្តល់​ឈ្មោះ​ដំណើរការ​របស់​ប្រព័ន្ធ​ទៅ​កាន់
 * GmsSupport.currentProcessName() ដែល​អាន​ឈ្មោះ​ពិត​រួច​កាត់ ".morphe"
 * ចេញ​ពី​ផ្នែក​កញ្ចប់ (រក្សា​ផ្នែក​ក្រោយ ":" ដូច​ជា ":pushservice" ទុក)។
 * នៅ​ពេល​មិនមែន​ជា​ក្លូន តម្លៃ​ត្រឡប់​គឺ​ដូច​ដើម​ទាំងស្រុង → បំណះ​មិន​មាន​ផល​ប៉ះពាល់។
 *
 * គ្របដណ្តប់​ផ្លូវ​ពីរ​ដែល​កម្មវិធី Android ប្រើ​៖
 *  - Application.getProcessName() (API 28+)
 *  - ActivityThread.currentProcessName() (hidden API ដែល AndroidX ប្រើ​ពេល​ហៅ​ផ្ទាល់)
 */
@Suppress("unused")
val processNameSpoofingPatch = bytecodePatch(
    name = "Process Name Spoofing for Clone Support",
    description = "For cloned builds (package suffix such as .morphe), strips the clone suffix from " +
        "the process name the app reads through Application.getProcessName()/ActivityThread, so " +
        "process-name based routing behaves exactly like the original install. No-op on normal, " +
        "uncloned builds.",
) {
    compatibleWith(swiftKeyCompatibility)
    extendWith("extensions/swiftkey.mpe")

    execute {
        val replaced = redirectInvokes(
            InvokeRedirect(
                definingClass = "Landroid/app/Application;",
                name = "getProcessName",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
                replacementDescriptor = EXTENSION,
                replacementName = "currentProcessName",
            ),
            InvokeRedirect(
                definingClass = "Landroid/app/ActivityThread;",
                name = "currentProcessName",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
                replacementDescriptor = EXTENSION,
                replacementName = "currentProcessName",
            ),
        )

        // មិន​បង្ខំ​បរាជ័យ​ពេល​រក​មិនឃើញ ៖ កម្មវិធី​ខ្លះ​ប្រើ​តែ​ការ​ឆ្លុះ/androidx
        // (គ្មាន​សេចក្តី​ណែនាំ​ហៅ​ផ្ទាល់​ដែល​អាច​កែ​បាន)។ ក្លូន​ធម្មតា​ដែល​គ្មាន
        // process routing ក៏​មិន​ត្រូវការ​បំណះ​នេះ​ដែរ។
        println("[ProcessNameSpoof] Redirected $replaced process-name provider call sites")
    }
}
