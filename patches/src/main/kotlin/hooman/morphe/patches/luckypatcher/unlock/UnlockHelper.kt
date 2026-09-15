package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c

/**
 * 🍀 Shared helper for Lucky Patcher unlock patches
 *
 * Support all version strategy:
 * 1. Fingerprint-based patching (best-effort)
 * 2. Generic scanning: any method returning Z/I that contains VIP/Pro/Premium strings -> force true/high value
 * 3. Billing/License bypass
 * 4. Soft-fail — never crash, just log warning if nothing found
 */

internal data class PatchStats(
    var patched: Int = 0,
    val alreadyPatched: MutableSet<String> = mutableSetOf(),
)

internal fun PatchContext.patchViaFingerprints(
    stats: PatchStats,
    vararg fingerprintPairs: Pair<app.morphe.patcher.Fingerprint, Boolean>,
) {
    fingerprintPairs.forEach { (fingerprint, forceTrue) ->
        fingerprint.methodOrNull?.let { method ->
            if (method.implementation == null) return@let
            val methodKey = "${method.definingClass}->${method.name}${method.parameterTypes}${method.returnType}"
            if (methodKey in stats.alreadyPatched) return@let

            val patchCode = if (forceTrue) {
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent()
            } else {
                """
                    const/4 v0, 0x0
                    return v0
                """.trimIndent()
            }

            try {
                method.addInstructions(0, patchCode)
                stats.patched++
                stats.alreadyPatched.add(methodKey)
                println("[LuckyPatcher] Patched via fingerprint ${fingerprint.javaClass.simpleName} -> $method")
            } catch (e: Exception) {
                println("[LuckyPatcher] Failed fingerprint ${fingerprint.javaClass.simpleName} $method: ${e.message}")
            }
        }
    }
}

internal fun PatchContext.patchIntViaFingerprints(
    stats: PatchStats,
    vararg fingerprints: app.morphe.patcher.Fingerprint,
    returnValue: Int = 1,
) {
    fingerprints.forEach { fingerprint ->
        fingerprint.methodOrNull?.let { method ->
            if (method.implementation == null) return@let
            val methodKey = "${method.definingClass}->${method.name}${method.parameterTypes}${method.returnType}"
            if (methodKey in stats.alreadyPatched) return@let

            val patchCode = if (returnValue <= 1) {
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent()
            } else {
                """
                    const v0, $returnValue
                    return v0
                """.trimIndent()
            }

            try {
                method.addInstructions(0, patchCode)
                stats.patched++
                stats.alreadyPatched.add(methodKey)
                println("[LuckyPatcher] Patched int via fingerprint ${fingerprint.javaClass.simpleName} -> $method returns $returnValue")
            } catch (e: Exception) {
                println("[LuckyPatcher] Failed int fingerprint $method: ${e.message}")
            }
        }
    }
}

internal fun PatchContext.genericScanAndPatch(
    stats: PatchStats,
    booleanIndicators: Set<String>,
    intIndicators: Set<String>,
    intReturnValue: Int = 1,
    booleanReturnTrue: Boolean = true,
    tag: String = "LuckyPatcher",
) {
    // Avoid extension code
    getAllClassesWithStrings().forEach { classDef ->
        if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach

        val mutableClass = try {
            mutableClassDefBy(classDef)
        } catch (_: Exception) {
            return@forEach
        }

        classDef.methods.forEach { originalMethod ->
            val impl = originalMethod.implementation ?: return@forEach
            if (AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) || AccessFlags.NATIVE.isSet(originalMethod.accessFlags)) return@forEach

            val methodKey = "${classDef.type}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
            if (methodKey in stats.alreadyPatched) return@forEach

            // Collect string constants
            val stringsInMethod = mutableSetOf<String>()
            impl.instructions.forEach { insn ->
                try {
                    val ref = (insn as? Instruction21c)?.reference as? StringReference
                    ref?.string?.let { stringsInMethod.add(it) }
                } catch (_: Exception) {}
                try {
                    val ref = (insn as? Instruction31c)?.reference as? StringReference
                    ref?.string?.let { stringsInMethod.add(it) }
                } catch (_: Exception) {}
            }

            if (stringsInMethod.isEmpty()) return@forEach

            val hasBooleanString = stringsInMethod.any { s ->
                booleanIndicators.any { ind -> s.contains(ind, ignoreCase = false) || s.equals(ind, ignoreCase = true) }
            }
            val hasIntString = stringsInMethod.any { s ->
                intIndicators.any { ind -> s.contains(ind, ignoreCase = false) || s.equals(ind, ignoreCase = true) }
            }

            val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                candidate.name == originalMethod.name &&
                    candidate.returnType == originalMethod.returnType &&
                    candidate.parameterTypes.size == originalMethod.parameterTypes.size &&
                    candidate.parameterTypes.zip(originalMethod.parameterTypes).all { (a, b) -> a.toString() == b.toString() }
            } ?: return@forEach

            try {
                when {
                    originalMethod.returnType == "Z" && hasBooleanString -> {
                        val code = if (booleanReturnTrue) "const/4 v0, 0x1\nreturn v0" else "const/4 v0, 0x0\nreturn v0"
                        mutableMethod.addInstructions(0, code)
                        stats.patched++
                        stats.alreadyPatched.add(methodKey)
                    }
                    originalMethod.returnType == "I" && (hasBooleanString || hasIntString) -> {
                        val code = if (intReturnValue <= 1) "const/4 v0, 0x1\nreturn v0" else "const v0, $intReturnValue\nreturn v0"
                        mutableMethod.addInstructions(0, code)
                        stats.patched++
                        stats.alreadyPatched.add(methodKey)
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}

internal fun PatchContext.genericBillingBypass(
    stats: PatchStats,
) {
    // Additional generic bypass for Google Play Billing / LVL
    // Patch any method that looks like billing check to return true
    val billingIndicators = setOf(
        "is_purchased", "isPurchased", "has_purchase", "is_billing_available",
        "isLicensed", "is_licensed", "check_license", "LICENSED",
        "purchase_state", "is_premium_purchased",
    )

    getAllClassesWithStrings().forEach { classDef ->
        if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach
        // Focus on billing related classes to reduce false positives
        val isBillingClass = classDef.type.contains("billing", ignoreCase = true) ||
            classDef.type.contains("purchase", ignoreCase = true) ||
            classDef.type.contains("license", ignoreCase = true) ||
            classDef.type.contains("vending", ignoreCase = true) ||
            classDef.type.contains("iap", ignoreCase = true)

        if (!isBillingClass) return@forEach

        val mutableClass = try {
            mutableClassDefBy(classDef)
        } catch (_: Exception) {
            return@forEach
        }

        classDef.methods.forEach { originalMethod ->
            val impl = originalMethod.implementation ?: return@forEach
            if (AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) || AccessFlags.NATIVE.isSet(originalMethod.accessFlags)) return@forEach
            if (originalMethod.returnType != "Z" && originalMethod.returnType != "I") return@forEach

            val methodKey = "${classDef.type}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
            if (methodKey in stats.alreadyPatched) return@forEach

            val stringsInMethod = mutableSetOf<String>()
            impl.instructions.forEach { insn ->
                try {
                    val ref = (insn as? Instruction21c)?.reference as? StringReference
                    ref?.string?.let { stringsInMethod.add(it) }
                } catch (_: Exception) {}
                try {
                    val ref = (insn as? Instruction31c)?.reference as? StringReference
                    ref?.string?.let { stringsInMethod.add(it) }
                } catch (_: Exception) {}
            }

            val hasBillingString = stringsInMethod.any { s ->
                billingIndicators.any { ind -> s.contains(ind, ignoreCase = true) }
            }

            // Also check method name itself
            val methodNameLower = originalMethod.name.lowercase()
            val isBillingMethodName = methodNameLower.contains("purchas") ||
                methodNameLower.contains("billing") ||
                methodNameLower.contains("licensed") ||
                methodNameLower.contains("license") ||
                methodNameLower.contains("ispro") ||
                methodNameLower.contains("ispremium") ||
                methodNameLower.contains("isvip")

            if (!hasBillingString && !isBillingMethodName) return@forEach

            val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                candidate.name == originalMethod.name &&
                    candidate.returnType == originalMethod.returnType &&
                    candidate.parameterTypes.size == originalMethod.parameterTypes.size
            } ?: return@forEach

            try {
                when (originalMethod.returnType) {
                    "Z" -> {
                        mutableMethod.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                        stats.patched++
                        stats.alreadyPatched.add(methodKey)
                    }
                    "I" -> {
                        // For license check, 0 often means LICENSED, 1 also means licensed in some impl
                        // We return 0 for LICENSED (Google LVL: 0=LICENSED, 1=NOT_LICENSED, 2=RETRY)
                        // But for generic int billing, return 1 for success
                        // Use heuristic: if class contains license, return 0, else 1
                        val returnVal = if (classDef.type.contains("license", ignoreCase = true)) 0 else 1
                        val code = "const/4 v0, $returnVal\nreturn v0"
                        mutableMethod.addInstructions(0, code)
                        stats.patched++
                        stats.alreadyPatched.add(methodKey)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
