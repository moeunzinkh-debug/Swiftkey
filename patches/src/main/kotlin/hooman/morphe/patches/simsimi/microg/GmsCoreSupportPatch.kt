package hooman.morphe.patches.simsimi.microg

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import hooman.morphe.patches.simsimi.simsimiCompatibility
import hooman.morphe.patches.simsimi.support.simsimiSupportManifestPatch
import hooman.morphe.patches.simsimi.support.spoofedSignatureProvider

@Suppress("unused")
val gmsCoreSupportPatch = bytecodePatch(
    name = "GmsCore support (MicroG RE)",
    description = "Umbrella patch pulling in 2+3+4 plus the shared manifest patch: <queries> visibility " +
        "for GmsCore/speech services, account permissions, c2dm permission rename. " +
        "Option: \"Original signing certificate SHA-1\" (cloned builds only). Requires MicroG RE 7.x installed.",
) {
    compatibleWith(simsimiCompatibility)

    val originalSignatureOption = stringOption(
        key = "originalSigningSha1",
        default = null,
        title = "Original signing certificate SHA-1",
        description = "Only needed when the patched APK is cloned/renamed (e.g. with the Morphe " +
            "\"Clone app\" patch). GmsCore presents this SHA-1 to Google services as the app's " +
            "real identity. Read it from the ORIGINAL, unmodified SimSimi APK with: " +
            "apksigner verify --print-cert SimSimi.apk (use the SHA-1, hex, colon-separated). " +
            "Leave empty for a normal, un-cloned install.",
        required = false,
    )

    dependsOn(
        simsimiSupportManifestPatch,
        gmsCoreBytecodeRedirectPatch,
        gmsCoreAvailabilityBypassPatch,
        microgAccountPermissionsPatch,
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
            "[GmsCoreSupport][SimSimi] MicroG RE support armed" +
                if (signature.isEmpty()) " (normal install, no clone spoof)" else " (clone spoof enabled)",
        )
    }
}
