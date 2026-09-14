package hooman.morphe.patches.swiftkey.microg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility

/**
 * GmsCore Signature and Availability Bypass
 *
 * កម្មវិធី​ដែល​ភ្ជាប់​ Firebase/GMS ហៅ​ GoogleApiAvailability មុន​ប្រើ​សេវា។
 * ពេល​ប្រព័ន្ធ​មិនមាន Google Play Services ផ្លូវការ (ប្រើ GmsCore ជំនួស ឬ​គ្មាន​ទាំងស្រុង)
 * តួ​ទាំងនោះ​ត្រឡប់​កូដ​កំហុស/បោះ​សារ​ឲ្យ​ឈប់ បើ​ទោះបី​មុខងារ​ដែល SwiftKey ត្រូវការ
 * (ជាពិសេស​មិនមាន​មុខងារ​ចាំបាច់​ខាង server) មិន​ត្រូវ​ការ​ការត្រួតពិនិត្យ​នោះ​ក៏​ដោយ។
 *
 * បំណះ​បិទ​តួ​ត្រួតពិនិត្យ​ពីរ (បន្ទាប់ពី​ឆ្លាស់​ទិស​កញ្ចប់​ទៅ GmsCore រួច)៖
 *  - មេតូដ void៖ return-void ត្រឹម​ចំណុច​ចូល
 *  - មេតូដ​លេខ (static, ប៉ារ៉ាម៉ែត្រ L,I)៖ សរសេរ 0 លើ​ប៉ារ៉ាម៉ែត្រ int (p1) រួច​ត្រឡប់​វា​វិញ
 *    (ប្រើ​ប៉ារ៉ាម៉ែត្រ​ផ្ទាល់ ជៀសវាង​ការ​សន្មត់​ថា​មាន register ទំនេរ)
 *
 * "Signature bypass" ៖ ការ​ផ្ទៀងផ្ទាត់​ហត្ថលេខា​ GMS (GoogleSignatureVerifier) ត្រូវ​បាន
 *​បិទ​ជា​ស្វ័យប្រវត្តិ​ដោយ​ការ​ឆ្លាស់​ទិស — កម្មវិធី​ភ្ជាប់​ទៅ GmsCore ត្រង់ៗ (ដែល
 * ចុះហត្ថលេខា​ដោយ​ស្រប​ច្បាប់​ក្នុង​នាម app.revanced) ដូច្នេះ​គ្មាន​ផ្លូវ​ហៅ​ទៅ​កាន់
 * GMS ផ្លូវការ​ដើម្បី​ផ្ទៀងផ្ទាត់​នៅ​សល់។ SwiftKey ជា​កម្មវិធី Microsoft មិនមាន
 * ការ​ផ្ទៀងផ្ទាត់​ហត្ថលេខា​ GMS ផ្ទាល់ខ្លួន​ទេ (ខុស​ពី​កម្មវិធី Google ទីមួយ)។
 */
@Suppress("unused")
val gmsCoreAvailabilityBypassPatch = bytecodePatch(
    name = "GmsCore Signature and Availability Bypass",
    description = "Stops the bundled Google Play Services availability/version checks from " +
        "blocking the app when Google Play Services is absent and GmsCore (MicroG RE) takes its " +
        "place: the enforce check returns immediately and the availability result reads " +
        "SUCCESS (0). Checks that this build does not include are skipped instead of failing.",
) {
    compatibleWith(swiftKeyCompatibility)

    execute {
        var patched = 0

        GmsAvailabilityFingerprints.serviceCheck.methodOrNull?.let { method ->
            // static (Context, int) void — ប៉ារ៉ាម៉ែត្រ​គឺ p0=Context, p1=int។
            method.addInstructions(0, "return-void")
            patched++
        } ?: println("[GmsAvailability] Service check method not present; skipping.")

        GmsAvailabilityFingerprints.googlePlayUtility.methodOrNull?.let { method ->
            // សរសេរ 0 (ConnectionResult.SUCCESS) លើ p1 ហើយ​ត្រឡប់ p1 — មិន​ប៉ះ p0។
            method.addInstructions(
                0,
                """
                    const/4 p1, 0x0
                    return p1
                """,
            )
            patched++
        } ?: println("[GmsAvailability] GooglePlayServicesUtility value method not present; skipping.")

        // ទោះ​គ្មាន​តួ​ទាំងពីរ (build អ្នក​កាត់​បណ្ណាល័យ​នេះ​ចោល) ក៏​មិនមែន​ជា​កំហុស
        // សម្រាប់ SwiftKey ដែរ — គ្រាន់តែ​កត់ត្រា។ ប៉ុន្តែ​កំណែ​គោលដៅ​ដែល​មាន Firebase
        // ត្រូវតែ​មាន​យ៉ាងហោចណាស់​មួយ; បើ​សូន្យ​ទាំងស្រុង ស្នាម​ប្រហែល​ជា​ផ្លាស់ — ព្រមាន។
        if (patched == 0) {
            println(
                "[GmsAvailability] WARNING: neither availability check fingerprint matched. " +
                    "The play-services-basement layout changed; the bypass had nothing to patch.",
            )
        }
    }
}
