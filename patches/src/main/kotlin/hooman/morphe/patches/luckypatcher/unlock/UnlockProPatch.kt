package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibility
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantBilling
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantRu

/**
 * 🍀 Unlock Pro — Lucky Patcher
 *
 * បំណះនេះ unlock pro features ដោយ force boolean checks ឲ្យ true
 * និង int level ឲ្យ 1/999។ Support all version តាមរយៈ generic scanning។
 *
 * Features:
 * - isPro, is_pro, pro_user, has_pro, pro_version → true
 * - pro_level, pro_type, pro_status → 1 (ឬ 999)
 * - billing/license bypass → true
 *
 * Tag: Lucky Patcher
 */
@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro (Lucky Patcher)",
    description = "Unlocks Pro features (isPro, pro_user, pro_version) by forcing boolean checks to true and level to 1. Supports all versions via generic scanning — works on any build including R8 obfuscated. Tag: Lucky Patcher.",
) {
    compatibleWith(luckyPatcherCompatibility, luckyPatcherCompatibilityVariantRu, luckyPatcherCompatibilityVariantBilling)

    execute {
        val stats = PatchStats()

        // 1. Fingerprint-based
        patchViaFingerprints(
            stats,
            ProBooleanFingerprint to true,
            VipBooleanFingerprint to true,
            BillingCheckFingerprint to true,
        )

        patchIntViaFingerprints(
            stats,
            ProLevelIntFingerprint,
            VipLevelIntFingerprint,
            returnValue = 1,
        )

        // 2. Generic scanning for Pro
        val proBooleanIndicators = setOf(
            "is_pro", "isPro", "is_pro_user", "isProUser", "pro_user",
            "is_pro_version", "isProVersion", "pro_version", "has_pro", "hasPro",
            "pro_enabled", "is_pro_enabled", "pro_status", "is_pro_status",
            "is_pro_active", "pro_active", "check_pro",
        )

        val proIntIndicators = setOf(
            "pro_type", "pro_level", "get_pro_type", "getProType",
            "get_pro_level", "getProLevel", "pro_status", "pro_tier",
        )

        genericScanAndPatch(
            stats = stats,
            booleanIndicators = proBooleanIndicators,
            intIndicators = proIntIndicators,
            intReturnValue = 1,
            booleanReturnTrue = true,
            tag = "UnlockPro",
        )

        // 3. Billing bypass
        genericBillingBypass(stats)

        // 4. Result
        if (stats.patched == 0) {
            println(
                "[UnlockPro] WARNING: No Pro flag methods found. " +
                    "The app's pro check layout may have changed, but patch is designed for all versions. " +
                    "No crash — just nothing to patch in this build.",
            )
        } else {
            println("[UnlockPro] ✅ Patched ${stats.patched} methods to unlock Pro (all versions supported)")
        }
    }
}
