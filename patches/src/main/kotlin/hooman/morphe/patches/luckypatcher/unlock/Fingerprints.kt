package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * 🍀 Lucky Patcher Unlock Fingerprints
 *
 * សម្រាប់ support all version យើងប្រើ fingerprints ជាច្រើនប្រភេទ
 * ដែលស្វែងរកតាម string constants មិនមែនតាម method name រឹងមាំ
 * ព្រោះ Lucky Patcher និង app ផ្សេងៗដែលប្រើ Lucky Patcher style
 * អាច obfuscated ដោយ R8/ProGuard។
 */

// ---- Boolean VIP/Pro/Premium checks ----
internal object VipBooleanFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "is_vip", "isVip", "is_vip_user", "vip_user", "isVipUser",
        "vip_status", "is_vip_status", "has_vip", "hasVip",
        "is_pro", "isPro", "is_pro_user", "isProUser", "has_pro",
        "is_premium", "isPremium", "is_premium_user", "isPremiumUser",
        "has_premium", "hasPremium", "premium_user",
        "is_member", "isMember", "is_membership", "membership",
        "is_premium_status", "premium_status",
        "is_subscribed", "isSubscribed", "has_subscription",
        "is_paid", "isPaid", "is_purchased", "isPurchased",
        "check_vip", "check_premium", "check_pro",
    ),
    custom = { method, _ ->
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object ProBooleanFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "is_pro", "isPro", "is_pro_user", "isProUser", "is_pro_version",
        "isProVersion", "pro_version", "pro_user", "is_pro_enabled",
        "pro_enabled", "has_pro", "hasPro", "check_pro",
        "is_premium_pro", "premium_pro",
    ),
    custom = { method, _ ->
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object PremiumBooleanFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "is_premium", "isPremium", "is_premium_user", "isPremiumUser",
        "premium_user", "has_premium", "hasPremium",
        "is_premium_enabled", "premium_enabled",
        "is_premium_version", "isPremiumVersion",
        "premium_status", "is_premium_status",
        "check_premium", "is_premium_active",
    ),
    custom = { method, _ ->
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object MembershipBooleanFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "is_member", "isMember", "is_membership", "isMembership",
        "membership", "is_member_active", "isMemberActive",
        "has_membership", "hasMembership", "membership_status",
        "is_vip_member", "is_premium_member", "check_membership",
        "is_subscribed", "isSubscribed", "has_subscription",
        "subscription_status", "is_subscription_active",
    ),
    custom = { method, _ ->
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

// ---- Int level/type checks ----
internal object VipLevelIntFingerprint : Fingerprint(
    returnType = "I",
    strings = listOf(
        "vip_type", "vip_level", "vip_status", "get_vip_type", "getVipType",
        "get_vip_level", "getVipLevel", "membership_type", "premium_type",
        "user_level", "get_membership", "getMembership", "get_user_level",
        "pro_level", "premium_level", "vip_tier", "membership_tier",
    ),
    custom = { method, _ ->
        method.returnType == "I" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object ProLevelIntFingerprint : Fingerprint(
    returnType = "I",
    strings = listOf(
        "pro_type", "pro_level", "get_pro_type", "getProType",
        "get_pro_level", "getProLevel", "pro_status",
    ),
    custom = { method, _ ->
        method.returnType == "I" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

// ---- Billing / License checks (Lucky Patcher style) ----
internal object BillingCheckFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "billing", "is_purchased", "isPurchased", "is_billing_available",
        "isBillingAvailable", "has_purchase", "hasPurchase",
        "check_purchase", "checkPurchase", "verify_purchase",
        "purchase_state", "is_premium_purchased", "license_check",
        "is_licensed", "isLicensed", "check_license",
        "InAppBillingService", "IInAppBillingService",
        "com.android.vending.billing",
    ),
    custom = { method, _ ->
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object LicenseCheckFingerprint : Fingerprint(
    returnType = "I",
    strings = listOf(
        "LICENSED", "NOT_LICENSED", "RETRY", "license", "License",
        "checkLicense", "license_check", "verifyLicense",
        "com.android.vending.licensing",
        "LicenseValidator", "LicenseChecker",
    ),
    custom = { method, _ ->
        (method.returnType == "I" || method.returnType == "Z") &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)
