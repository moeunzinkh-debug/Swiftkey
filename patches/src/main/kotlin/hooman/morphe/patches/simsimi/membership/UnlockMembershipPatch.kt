package hooman.morphe.patches.simsimi.membership

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import hooman.morphe.patches.simsimi.simsimiCompatibility

/**
 * Unlock membership
 *
 * Forces the VIP flag to true so premium features are unlocked client-side.
 * Server-validated assets (cloud effects/templates) are not affected.
 *
 * Strategy:
 * 1. Try targeted fingerprints (boolean isVip / isPremium / isSubscribed) -> force true.
 * 2. Generic scan: any method returning boolean (Z) that contains VIP/premium/membership related
 *    string constants -> force true.
 * 3. Generic scan for int-returning VIP level/status methods -> force 1 (or high value).
 * 4. Soft-fail: if nothing found, log warning instead of crashing, because the app may have
 *    changed its flag names.
 */
@Suppress("unused")
val unlockMembershipPatch = bytecodePatch(
    name = "Unlock membership",
    description = "Forces the VIP flag to true so premium features are unlocked client-side. " +
        "Server-validated assets (cloud effects/templates) are not affected.",
) {
    compatibleWith(simsimiCompatibility)

    execute {
        var patched = 0

        // ---- 1. Fingerprint-based patching (best-effort) -----------------
        val fingerprintTargets = listOf(
            VipFlagFingerprint to true,
            SubscriptionCheckFingerprint to true,
        )

        fingerprintTargets.forEach { (fingerprint, forceTrue) ->
            fingerprint.methodOrNull?.let { method ->
                // Skip abstract/native already filtered, but double-check implementation exists.
                if (method.implementation == null) return@let

                // ប្រើ v0 ជានិច្ច — កុំ overwrite p0 (this) ក្នុង instance method
                val patchCode = """
                        const/4 v0, 0x1
                        return v0
                    """

                try {
                    method.addInstructions(0, patchCode)
                    patched++
                    println("[UnlockMembership] Patched via fingerprint ${fingerprint.hashCode()} -> $method")
                } catch (e: Exception) {
                    println("[UnlockMembership] Failed to patch fingerprint method $method: ${e.message}")
                }
            }
        }

        // Int-returning VIP level/status -> return 1 or high value
        VipStatusIntFingerprint.methodOrNull?.let { method ->
            if (method.implementation != null) {
                val patchCode = """
                        const/4 v0, 0x1
                        return v0
                    """
                try {
                    method.addInstructions(0, patchCode)
                    patched++
                    println("[UnlockMembership] Patched int VIP status via fingerprint -> $method")
                } catch (e: Exception) {
                    println("[UnlockMembership] Failed int patch $method: ${e.message}")
                }
            }
        }

        // ---- 2. Generic scanning fallback ---------------------------------
        // Strings that strongly indicate VIP/membership checks.
        val vipBooleanIndicators = setOf(
            "is_vip",
            "isVip",
            "is_vip_user",
            "isVipUser",
            "vip_user",
            "is_premium",
            "isPremium",
            "is_premium_user",
            "isPremiumUser",
            "premium_user",
            "is_pro",
            "isPro",
            "is_subscribed",
            "isSubscribed",
            "has_premium",
            "hasPremium",
            "is_member",
            "isMember",
            "vip_status",
            "premium_status",
            "membership",
            "is_vip_status",
            "check_vip",
            "check_premium",
            "has_vip",
            "hasVip",
        )

        val vipIntIndicators = setOf(
            "vip_type",
            "vip_level",
            "membership_type",
            "premium_type",
            "user_level",
            "get_vip_type",
            "getVipType",
            "get_membership",
            "getMembership",
            "vip_status",
            "get_vip_status",
        )

        // Scan all classes with strings to avoid full DEX scan cost.
        val alreadyPatched = mutableSetOf<String>()

        getAllClassesWithStrings().forEach { classDef ->
            // Skip extension code
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach

            val mutableClass = try {
                mutableClassDefBy(classDef)
            } catch (_: Exception) {
                return@forEach
            }

            classDef.methods.forEach { originalMethod ->
                val impl = originalMethod.implementation ?: return@forEach
                if (AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) || AccessFlags.NATIVE.isSet(originalMethod.accessFlags)) return@forEach

                // Avoid double-patching same method signature
                val methodKey = "${classDef.type}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
                if (methodKey in alreadyPatched) return@forEach

                // Collect string constants in method
                val stringsInMethod = impl.instructions.mapNotNull { insn ->
                    try {
                        val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference
                        ref?.string
                    } catch (_: Exception) {
                        try {
                            val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference
                            ref?.string
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.toSet()

                if (stringsInMethod.isEmpty()) return@forEach

                val hasVipBooleanString = stringsInMethod.any { s -> vipBooleanIndicators.any { ind -> s.contains(ind, ignoreCase = false) || s.equals(ind, ignoreCase = true) } }
                val hasVipIntString = stringsInMethod.any { s -> vipIntIndicators.any { ind -> s.contains(ind, ignoreCase = false) || s.equals(ind, ignoreCase = true) } }

                val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                    candidate.name == originalMethod.name &&
                        candidate.returnType == originalMethod.returnType &&
                        candidate.parameterTypes.size == originalMethod.parameterTypes.size &&
                        candidate.parameterTypes.zip(originalMethod.parameterTypes).all { (a, b) -> a.toString() == b.toString() }
                } ?: return@forEach

                if (originalMethod.returnType == "Z" && hasVipBooleanString) {
                    val patchCode = """
                            const/4 v0, 0x1
                            return v0
                        """
                    try {
                        mutableMethod.addInstructions(0, patchCode)
                        patched++
                        alreadyPatched.add(methodKey)
                    } catch (_: Exception) {
                        // ignore
                    }
                } else if (originalMethod.returnType == "I" && (hasVipBooleanString || hasVipIntString)) {
                    // For int VIP level, return 1 (or 3 for higher tier) - use 1 for safe
                    val patchCode = """
                            const/4 v0, 0x1
                            return v0
                        """
                    try {
                        mutableMethod.addInstructions(0, patchCode)
                        patched++
                        alreadyPatched.add(methodKey)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // ---- 3. Result handling -------------------------------------------
        if (patched == 0) {
            println(
                "[UnlockMembership] WARNING: No VIP/premium flag methods were found to patch. " +
                    "The app's membership check layout may have changed; the unlock had nothing to patch. " +
                    "Client-side VIP features will remain locked.",
            )
        } else {
            println("[UnlockMembership] Patched $patched VIP/membership check methods to return true/1")
        }
    }
}
