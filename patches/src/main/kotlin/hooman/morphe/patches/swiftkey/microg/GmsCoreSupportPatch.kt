package hooman.morphe.patches.swiftkey.microg

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import hooman.morphe.patches.swiftkey.support.spoofedSignatureProvider
import hooman.morphe.patches.swiftkey.support.swiftKeySupportManifestPatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility

/**
 * GmsCore support (MicroG RE)
 *
 * បំណះ​ឆ័ត្រ​មួយ​ដែល​ទាញ​យក​ក្រុម​បំណះ microG/GmsCore​ទាំងអស់​សម្រាប់ SwiftKey
 * (សម្រាប់​ឧបករណ៍​គ្មាន Google Play Services ដែល​ដំឡើង MicroG RE — កញ្ចប់
 * app.revanced.android.gms)៖
 *
 *  1. [gmsCoreBytecodeRedirectPatch] — ប្ដូរ​ទិស​រាល់​សេចក្តីយោង GMS ទៅ GmsCore
 *  2. [gmsCoreAvailabilityBypassPatch] — បិទ​ការ​ត្រួតពិនិត្យ​ភាព​មាន/ហត្ថលេខា​ GMS
 *  3. [microgAccountPermissionsPatch] — ស្នើ​សិទ្ធិ​គណនី GmsCore ពេល​រត់
 *  4. [processNameSpoofingPatch] — គាំទ្រ​ការ​ក្លូន (clone) តាម​រយៈ​ការ​កាត់​បច្ច័យ
 *     ឈ្មោះ​ដំណើរការ
 *  5. swiftKeySupportManifestPatch — ការកែ manifest ដែល​ត្រូវគ្នា (មើល​ផ្នែក support)
 *
 * កំណត់​សម្គាល់​សំខាន់ៗ
 *  - SwiftKey ជា​កម្មវិធី Microsoft៖ ការ​ចូល​គណនី Microsoft/cloud sync ដើរ​តាម
 *    ម៉ាស៊ីន Microsoft មិនមែន GMS — បំណះ​នេះ​មិន​ដោះ sync ទេ។
 *  - Push (FCM) នឹង​ដើរ​តាម​រយៈ GmsCore វិញ​ពេល​ឆ្លាស់​ទិស​សិទ្ធិ/កញ្ចប់។
 *  - សម្រាប់​ការ​ក្លូន (ប្តូរ​ឈ្មោះ​កញ្ចប់) ត្រូវ​បំពេញ option ហត្ថលេខា​ SHA-1
 *    ដើម (អាន​ពី APK មុន patch៖ `apksigner verify --print-cert`) ដើម្បី​ឲ្យ
 *    GmsCore ឆ្លុះ​អត្តសញ្ញាណ​ដើម​ទៅកាន់ Google services។
 */
@Suppress("unused")
val gmsCoreSupportPatch = bytecodePatch(
    name = "GmsCore support (MicroG RE)",
    description = "Umbrella patch for devices running MicroG RE (app.revanced.android.gms) instead " +
        "of Google Play Services. Pulls in: GmsCore bytecode redirect, GmsCore signature and " +
        "availability bypass, MicroG account permissions and clone process-name spoofing, plus " +
        "the manifest queries/permissions they need. Requires MicroG RE 7.x installed; it does " +
        "not provide Microsoft account/cloud features.",
) {
    compatibleWith(swiftKeyCompatibility)

    val originalSignatureOption = stringOption(
        key = "originalSigningSha1",
        default = null,
        title = "Original signing certificate SHA-1 (cloned builds only)",
        description = "Only needed when the patched APK is cloned/renamed (e.g. with the Morphe " +
            "\"Clone app\" patch). GmsCore presents this SHA-1 to Google services as the app's " +
            "real identity. Read it from the ORIGINAL, unmodified SwiftKey 9.13.13.5 APK with: " +
            "apksigner verify --print-cert SwiftKey.apk (use the SHA-1, hex, colon-separated). " +
            "Leave empty for a normal, un-cloned install.",
        required = false,
    )

    dependsOn(
        swiftKeySupportManifestPatch,
        gmsCoreBytecodeRedirectPatch,
        gmsCoreAvailabilityBypassPatch,
        microgAccountPermissionsPatch,
        processNameSpoofingPatch,
    )

    execute {
        val signature = originalSignatureOption.value?.trim().orEmpty()

        if (signature.isNotEmpty() &&
            !signature.matches(Regex("(?i)^[0-9a-f]{2}(?::[0-9a-f]{2}){19}$"))
        ) {
            throw PatchException(
                "The \"Original signing certificate SHA-1\" option is not a valid SHA-1 " +
                    "(expected 20 colon-separated hex bytes, e.g. AB:CD:..:. Got: " +
                    "\"${originalSignatureOption.value}\").",
            )
        }

        spoofedSignatureProvider = { signature.ifEmpty { null } }

        println(
            "[GmsCoreSupport] MicroG RE support armed" +
                if (signature.isEmpty()) " (normal install, no clone spoof)" else " (clone spoof enabled)",
        )
    }
}
