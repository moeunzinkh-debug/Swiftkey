package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibility
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantBilling
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantRu

/**
 * 👑 Unlock Membership — Lucky Patcher
 *
 * Unlock membership/subscription: isMember, isMembership, membership, isSubscribed → true
 * membership_type, subscription_status → 1
 * Support all version via generic scanning.
 */
@Suppress("unused")
val unlockMembershipPatch = bytecodePatch(
    name = "Unlock Membership (Lucky Patcher)",
    description = "Unlocks Membership/Subscription (isMember, membership, isSubscribed) by forcing checks to true and tier to 1. Supports all versions via generic scanning. Tag: Lucky Patcher.",
) {
    compatibleWith(luckyPatcherCompatibility, luckyPatcherCompatibilityVariantRu, luckyPatcherCompatibilityVariantBilling)

    execute {
        val stats = PatchStats()

        // 1. Fingerprint-based
        patchViaFingerprints(
            stats,
            MembershipBooleanFingerprint to true,
            VipBooleanFingerprint to true,
            BillingCheckFingerprint to true,
        )

        patchIntViaFingerprints(
            stats,
            VipLevelIntFingerprint,
            ProLevelIntFingerprint,
            returnValue = 1,
        )

        // 2. Generic scanning for Membership
        val membershipBooleanIndicators = setOf(
            "is_member", "isMember", "is_membership", "isMembership",
            "membership", "is_member_active", "isMemberActive",
            "has_membership", "hasMembership", "membership_status",
            "is_membership_active", "membership_active",
            "is_vip_member", "is_premium_member", "check_membership",
            "is_subscribed", "isSubscribed", "has_subscription",
            "is_subscription_active", "subscription_active",
            "subscription_status", "is_subscribed_user",
            "has_active_subscription", "is_premium_member",
            "is_pro_member", "member_status",
        )

        val membershipIntIndicators = setOf(
            "membership_type", "membership_level", "get_membership_type",
            "getMembershipType", "membership_tier", "get_membership",
            "getMembership", "subscription_type", "subscription_tier",
            "user_level", "vip_level", "premium_level", "pro_level",
            "member_level", "get_member_level",
        )

        genericScanAndPatch(
            stats = stats,
            booleanIndicators = membershipBooleanIndicators,
            intIndicators = membershipIntIndicators,
            intReturnValue = 1,
            booleanReturnTrue = true,
            tag = "UnlockMembership",
        )

        // 3. Billing bypass
        genericBillingBypass(stats)

        if (stats.patched == 0) {
            println(
                "[UnlockMembership] WARNING: No Membership flag methods found. " +
                    "Patch supports all versions — soft-fail.",
            )
        } else {
            println("[UnlockMembership] ✅ Patched ${stats.patched} methods to unlock Membership (all versions)")
        }
    }
}
