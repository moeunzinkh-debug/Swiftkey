package hooman.morphe.patches.simsimi.membership

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Generic VIP/premium checks - methods returning boolean that reference VIP-related strings.
// These are best-effort; R8 may rename methods but string constants remain.

internal object VipFlagFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "is_vip",
        "isVip",
        "vip",
        "is_premium",
        "isPremium",
        "premium",
        "is_subscribed",
        "isSubscribed",
        "is_pro",
        "isPro",
        "membership",
        "is_member",
        "vip_status",
        "premium_status",
        "is_vip_user",
        "has_premium",
    ),
    custom = { method, _ ->
        // Only boolean-returning, non-abstract methods.
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object VipStatusIntFingerprint : Fingerprint(
    returnType = "I",
    strings = listOf(
        "vip_type",
        "vip_status",
        "membership_type",
        "premium_type",
        "user_level",
        "vip_level",
        "is_vip",
        "is_premium",
    ),
    custom = { method, _ ->
        method.returnType == "I" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)

internal object SubscriptionCheckFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf(
        "subscription",
        "subscribed",
        "has_subscription",
        "is_subscribed",
        "billing",
        "purchase",
        "is_purchased",
        "entitlement",
    ),
    custom = { method, _ ->
        method.returnType == "Z" &&
            !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
            !AccessFlags.NATIVE.isSet(method.accessFlags)
    },
)
