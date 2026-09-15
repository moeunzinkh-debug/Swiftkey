package hooman.morphe.patches.simsimi.microg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.simsimi.simsimiCompatibility

@Suppress("unused")
val gmsCoreAvailabilityBypassPatch = bytecodePatch(
    name = "GmsCore Signature and Availability Bypass",
    description = "Neutralize the bundled play-services-basement availability/enforce checks: the " +
        "enforce check returns immediately and the availability result reads SUCCESS (0), so the app " +
        "runs when GmsCore replaces real GMS. Checks absent from this build must be soft-skipped, not fatal.",
) {
    compatibleWith(simsimiCompatibility)

    execute {
        var patched = 0

        GmsAvailabilityFingerprints.serviceCheck.methodOrNull?.let { method ->
            method.addInstructions(0, "return-void")
            patched++
        } ?: println("[GmsAvailability][SimSimi] Service check method not present; skipping.")

        GmsAvailabilityFingerprints.googlePlayUtility.methodOrNull?.let { method ->
            method.addInstructions(
                0,
                """
                    const/4 p1, 0x0
                    return p1
                """,
            )
            patched++
        } ?: println("[GmsAvailability][SimSimi] GooglePlayServicesUtility value method not present; skipping.")

        if (patched == 0) {
            println(
                "[GmsAvailability][SimSimi] WARNING: neither availability check fingerprint matched. " +
                    "The play-services-basement layout changed; the bypass had nothing to patch.",
            )
        } else {
            println("[GmsAvailability][SimSimi] Patched $patched availability checks")
        }
    }
}
