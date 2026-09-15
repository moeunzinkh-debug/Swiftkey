package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibility
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantBilling
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantRu

/**
 * 🍀 Unlock Pro — Lucky Patcher + APK Protection
 *
 * បំណះនេះ unlock pro features ដោយ force boolean checks ឲ្យ true
 * និង int level ឲ្យ 1/999។ Support all version តាមរយៈ generic scanning។
 * + ប្រព័ន្ធការពារ APK កុំឲ្យខូចពេល patch
 *
 * Features:
 * - isPro, is_pro, pro_user, has_pro, pro_version → true
 * - pro_level, pro_type, pro_status → 1 (ឬ 999)
 * - billing/license bypass → true
 *
 * 🛡️ Protection: backup/rollback, DEX validation, system class guard, 50/class limit
 *
 * Tag: Lucky Patcher
 */
@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro (Lucky Patcher)",
    description = "Unlocks Pro features (isPro, pro_user, pro_version) by forcing boolean checks to true and level to 1. Includes APK Protection: backup/rollback, DEX validation, system class guard. Supports all versions via generic scanning. Tag: Lucky Patcher.",
) {
    compatibleWith(luckyPatcherCompatibility, luckyPatcherCompatibilityVariantRu, luckyPatcherCompatibilityVariantBilling)

    execute {
        val stats = PatchStats()

        // 1. Fingerprint-based with protection
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

        // 2. Generic scanning for Pro with protection
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

        // 3. Billing bypass with protection
        genericBillingBypass(stats)

        // 4. Result + Protection Summary
        stats.protectionStats.printSummary("UnlockPro")

        if (stats.patched == 0) {
            println(
                "[UnlockPro] WARNING: No Pro flag methods found. " +
                    "The app's pro check layout may have changed, but patch is designed for all versions. " +
                    "🛡️ APK Protection ensured no corruption.",
            )
        } else {
            println("[UnlockPro] ✅ Patched ${stats.patched} methods to unlock Pro (all versions supported)")
            println("[UnlockPro] 🛡️ Protection: ${stats.protectionStats.totalSucceeded} ok, ${stats.protectionStats.totalFailed} fail, ${stats.protectionStats.totalSkippedSystem} system skipped")
        }
    }
}
