package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibility
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantBilling
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantRu

/**
 * 💎 Unlock Premium — Lucky Patcher
 *
 * Unlock premium features: isPremium, premium_user, has_premium, premium_status → true
 * premium_level, premium_type → 1
 * Support all version via generic scanning.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium (Lucky Patcher)",
    description = "Unlocks Premium features (isPremium, premium_user, has_premium) by forcing checks to true and level to 1. Supports all versions via generic scanning. Tag: Lucky Patcher.",
) {
    compatibleWith(luckyPatcherCompatibility, luckyPatcherCompatibilityVariantRu, luckyPatcherCompatibilityVariantBilling)

    execute {
        val stats = PatchStats()

        // 1. Fingerprint-based
        patchViaFingerprints(
            stats,
            PremiumBooleanFingerprint to true,
            VipBooleanFingerprint to true,
            BillingCheckFingerprint to true,
        )

        patchIntViaFingerprints(
            stats,
            VipLevelIntFingerprint,
            ProLevelIntFingerprint,
            returnValue = 1,
        )

        // 2. Generic scanning for Premium
        val premiumBooleanIndicators = setOf(
            "is_premium", "isPremium", "is_premium_user", "isPremiumUser",
            "premium_user", "has_premium", "hasPremium",
            "is_premium_enabled", "premium_enabled",
            "is_premium_version", "isPremiumVersion", "premium_version",
            "premium_status", "is_premium_status", "is_premium_active",
            "premium_active", "check_premium", "is_premium_purchased",
            "has_premium_access", "premium_access",
        )

        val premiumIntIndicators = setOf(
            "premium_type", "premium_level", "get_premium_type", "getPremiumType",
            "get_premium_level", "getPremiumLevel", "premium_status",
            "premium_tier", "get_membership", "membership_type",
            "user_level", "vip_level", "vip_type",
        )

        genericScanAndPatch(
            stats = stats,
            booleanIndicators = premiumBooleanIndicators,
            intIndicators = premiumIntIndicators,
            intReturnValue = 1,
            booleanReturnTrue = true,
            tag = "UnlockPremium",
        )

        // 3. Billing bypass
        genericBillingBypass(stats)

        if (stats.patched == 0) {
            println(
                "[UnlockPremium] WARNING: No Premium flag methods found. " +
                    "Patch supports all versions — soft-fail, no crash.",
            )
        } else {
            println("[UnlockPremium] ✅ Patched ${stats.patched} methods to unlock Premium (all versions)")
        }
    }
}
