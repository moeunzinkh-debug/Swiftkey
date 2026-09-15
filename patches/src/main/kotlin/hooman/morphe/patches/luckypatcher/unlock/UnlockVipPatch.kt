package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibility
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantBilling
import hooman.morphe.patches.luckypatcher.luckyPatcherCompatibilityVariantRu

/**
 * 🌟 Unlock VIP (All-in-One) — Lucky Patcher
 *
 * បំណះនេះជា All-in-One សម្រាប់ Lucky Patcher tag:
 * - Unlock VIP: isVip, vip_user, has_vip, vip_status → true, vip_level → 1/999
 * - Unlock Pro: isPro, pro_user, pro_version → true
 * - Unlock Premium: isPremium, premium_user → true
 * - Unlock Membership: isMember, membership, isSubscribed → true
 * - Billing/License Bypass: isPurchased, isLicensed, LICENSED → true/0
 *
 * Support all version: ប្រើ generic scanning + fingerprint soft-fail
 * មិនថាខេត្ត APK 9.1.8 ឬ 11.3.9 ឬ R8 obfuscated ក៏ដោយ — patch នឹងរក method
 * ដែលមាន string សម្គាល់ VIP/Pro/Premium/Membership ហើយ force return true.
 *
 * Tag: Lucky Patcher
 * List menu: បង្ហាញក្នុង Morphe Manager ក្រោម Lucky Patcher
 */
@Suppress("unused")
val unlockVipPatch = bytecodePatch(
    name = "Unlock VIP / Pro / Premium / Membership (Lucky Patcher)",
    description = "All-in-one unlock for VIP, Pro, Premium, Membership via Lucky Patcher style. Forces all VIP/Pro/Premium/Membership boolean checks to true, levels to 1, and bypasses billing/license checks. Supports ALL versions via generic scanning — no hard fingerprint, soft-fail safe. Tag: Lucky Patcher.",
) {
    compatibleWith(luckyPatcherCompatibility, luckyPatcherCompatibilityVariantRu, luckyPatcherCompatibilityVariantBilling)

    execute {
        val stats = PatchStats()

        // ---- 1. Fingerprint-based patching (best-effort) ----
        patchViaFingerprints(
            stats,
            VipBooleanFingerprint to true,
            ProBooleanFingerprint to true,
            PremiumBooleanFingerprint to true,
            MembershipBooleanFingerprint to true,
            BillingCheckFingerprint to true,
        )

        patchIntViaFingerprints(
            stats,
            VipLevelIntFingerprint,
            ProLevelIntFingerprint,
            returnValue = 1,
        )

        // License check returns 0 = LICENSED for Google LVL
        LicenseCheckFingerprint.methodOrNull?.let { method ->
            if (method.implementation != null) {
                val methodKey = "${method.definingClass}->${method.name}${method.parameterTypes}${method.returnType}"
                if (methodKey !in stats.alreadyPatched) {
                    try {
                        if (method.returnType == "I") {
                            method.addInstructions(0, "const/4 v0, 0x0\nreturn v0") // LICENSED = 0
                        } else if (method.returnType == "Z") {
                            method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                        }
                        stats.patched++
                        stats.alreadyPatched.add(methodKey)
                        println("[UnlockVIP] Patched license check via fingerprint -> $method")
                    } catch (e: Exception) {
                        println("[UnlockVIP] Failed license patch $method: ${e.message}")
                    }
                }
            }
        }

        // ---- 2. Generic scanning — comprehensive indicators ----
        val allBooleanIndicators = setOf(
            // VIP
            "is_vip", "isVip", "is_vip_user", "isVipUser", "vip_user", "is_vip_status",
            "vip_status", "has_vip", "hasVip", "vip_active", "is_vip_active",
            "check_vip", "vip_enabled", "is_vip_enabled", "isVipEnabled",
            // Pro
            "is_pro", "isPro", "is_pro_user", "isProUser", "pro_user", "has_pro", "hasPro",
            "is_pro_version", "isProVersion", "pro_version", "pro_enabled", "is_pro_enabled",
            "pro_status", "is_pro_status", "pro_active", "is_pro_active", "check_pro",
            // Premium
            "is_premium", "isPremium", "is_premium_user", "isPremiumUser", "premium_user",
            "has_premium", "hasPremium", "is_premium_enabled", "premium_enabled",
            "is_premium_version", "isPremiumVersion", "premium_version",
            "premium_status", "is_premium_status", "premium_active", "is_premium_active",
            "check_premium", "is_premium_purchased", "has_premium_access",
            // Membership / Subscription
            "is_member", "isMember", "is_membership", "isMembership", "membership",
            "is_member_active", "isMemberActive", "has_membership", "hasMembership",
            "membership_status", "is_membership_active", "is_vip_member",
            "is_premium_member", "check_membership", "is_subscribed", "isSubscribed",
            "has_subscription", "subscription_status", "is_subscription_active",
            "has_active_subscription", "member_status", "is_premium_member",
            // Paid / Purchased
            "is_paid", "isPaid", "is_purchased", "isPurchased", "has_purchase",
            "is_premium_paid", "is_pro_paid", "is_vip_paid",
            "is_entitled", "isEntitled", "has_entitlement",
            // Billing / License
            "is_billing_available", "isBillingAvailable", "is_licensed", "isLicensed",
            "license_check", "is_license_valid", "check_license",
            "is_premium_unlocked", "is_pro_unlocked", "is_vip_unlocked",
            "premium_unlocked", "pro_unlocked", "vip_unlocked",
        )

        val allIntIndicators = setOf(
            "vip_type", "vip_level", "vip_status", "get_vip_type", "getVipType",
            "get_vip_level", "getVipLevel", "vip_tier",
            "pro_type", "pro_level", "get_pro_type", "getProType", "get_pro_level", "pro_tier",
            "premium_type", "premium_level", "get_premium_type", "getPremiumType",
            "get_premium_level", "premium_tier",
            "membership_type", "membership_level", "membership_tier", "get_membership",
            "getMembership", "get_membership_type", "getMembershipType",
            "subscription_type", "subscription_tier", "user_level", "get_user_level",
            "member_level", "get_member_level", "user_tier",
        )

        genericScanAndPatch(
            stats = stats,
            booleanIndicators = allBooleanIndicators,
            intIndicators = allIntIndicators,
            intReturnValue = 1,
            booleanReturnTrue = true,
            tag = "UnlockVIP-All",
        )

        // ---- 3. Billing & License bypass (extra) ----
        genericBillingBypass(stats)

        // ---- 4. Extra: patch methods returning String like getMembershipStatus -> "premium" ----
        // This is optional but helps for some apps that check string status
        try {
            getAllClassesWithStrings().forEach { classDef ->
                if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach
                val mutableClass = try {
                    mutableClassDefBy(classDef)
                } catch (_: Exception) {
                    return@forEach
                }

                classDef.methods.forEach { originalMethod ->
                    if (originalMethod.returnType != "Ljava/lang/String;") return@forEach
                    val impl = originalMethod.implementation ?: return@forEach
                    if (com.android.tools.smali.dexlib2.AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) ||
                        com.android.tools.smali.dexlib2.AccessFlags.NATIVE.isSet(originalMethod.accessFlags)
                    ) return@forEach

                    val methodKey = "${classDef.type}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
                    if (methodKey in stats.alreadyPatched) return@forEach

                    val methodNameLower = originalMethod.name.lowercase()
                    val isStatusMethod = methodNameLower.contains("vip") ||
                        methodNameLower.contains("premium") ||
                        methodNameLower.contains("membership") ||
                        methodNameLower.contains("subscription") ||
                        methodNameLower.contains("pro") && methodNameLower.contains("status") ||
                        methodNameLower.contains("userlevel") ||
                        methodNameLower.contains("getstatus")

                    if (!isStatusMethod) return@forEach

                    val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                        candidate.name == originalMethod.name &&
                            candidate.returnType == originalMethod.returnType &&
                            candidate.parameterTypes.size == originalMethod.parameterTypes.size
                    } ?: return@forEach

                    try {
                        // Return "premium" or "vip" string for status methods
                        mutableMethod.addInstructions(0, "const-string v0, \"premium\"\nreturn-object v0")
                        stats.patched++
                        stats.alreadyPatched.add(methodKey)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        // ---- 5. Result handling ----
        if (stats.patched == 0) {
            println(
                "[UnlockVIP] WARNING: No VIP/Pro/Premium/Membership methods found to patch. " +
                    "This build may use server-side validation or completely different flag names. " +
                    "Patch supports ALL versions via generic scanning — soft-fail, no crash. " +
                    "Client-side features remain locked if no flags found.",
            )
        } else {
            println("[UnlockVIP] ✅ Patched ${stats.patched} methods — VIP/Pro/Premium/Membership unlocked! (All versions supported)")
        }
    }
}
