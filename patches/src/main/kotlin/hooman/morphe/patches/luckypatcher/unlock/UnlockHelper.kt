package hooman.morphe.patches.luckypatcher.unlock

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import hooman.morphe.patches.luckypatcher.support.createLuckyPatcherProtection
import hooman.morphe.patches.support.*

/**
 * 🍀 Shared helper for Lucky Patcher unlock patches — WITH APK PROTECTION
 *
 * Support all version + APK Protection strategy:
 * 1. Fingerprint-based patching with backup & validation
 * 2. Generic scanning with safe method validator
 * 3. Billing/License bypass with system class guard
 * 4. Soft-fail + rollback protection
 * 5. DEX integrity checks
 */

internal data class PatchStats(
    var patched: Int = 0,
    val alreadyPatched: MutableSet<String> = mutableSetOf(),
    val protectionStats: PatchProtectionStats = PatchProtectionStats(),
)

internal fun BytecodePatchContext.patchViaFingerprints(
    stats: PatchStats,
    vararg fingerprintPairs: Pair<app.morphe.patcher.Fingerprint, Boolean>,
) {
    val protection = createLuckyPatcherProtection()

    fingerprintPairs.forEach { (fingerprint, forceTrue) ->
        fingerprint.methodOrNull?.let { method ->
            val impl = method.implementation ?: return@let
            val classType = method.definingClass
            val methodKey = "${classType}->${method.name}${method.parameterTypes}${method.returnType}"

            // 🛡️ Protection check
            if (!protection.isSafeToPatchMethod(method, classType)) {
                println("[LuckyPatcher] 🛡️ Skipped unsafe fingerprint method $methodKey")
                return@let
            }

            if (methodKey in stats.alreadyPatched) return@let

            val patchCode = if (forceTrue) {
                "const/4 v0, 0x1\nreturn v0"
            } else {
                "const/4 v0, 0x0\nreturn v0"
            }

            try {
                // Backup
                val backupManager = protection.getBackupManager()
                backupManager.backup(methodKey, method)

                // Safe patch — safeAddInstructions រក mutable method តាមរយៈ context ខាងក្នុង
                val success = SafePatcher.safeAddInstructions(this, method, 0, patchCode, backupManager, methodKey)

                if (success && DexIntegrityChecker.validateMethodAfterPatch(method, classType)) {
                    stats.patched++
                    stats.alreadyPatched.add(methodKey)
                    stats.protectionStats.recordSuccess(classType)
                    println("[LuckyPatcher] ✅ Patched via fingerprint ${fingerprint.javaClass.simpleName} -> $method")
                } else {
                    stats.protectionStats.recordFailure()
                    println("[LuckyPatcher] ❌ Failed fingerprint ${fingerprint.javaClass.simpleName} $method")
                }
            } catch (e: Exception) {
                stats.protectionStats.recordFailure()
                println("[LuckyPatcher] ❌ Failed fingerprint ${fingerprint.javaClass.simpleName} $method: ${e.message}")
            }
        }
    }
}

internal fun BytecodePatchContext.patchIntViaFingerprints(
    stats: PatchStats,
    vararg fingerprints: app.morphe.patcher.Fingerprint,
    returnValue: Int = 1,
) {
    val protection = createLuckyPatcherProtection()

    fingerprints.forEach { fingerprint ->
        fingerprint.methodOrNull?.let { method ->
            val impl = method.implementation ?: return@let
            val classType = method.definingClass
            val methodKey = "${classType}->${method.name}${method.parameterTypes}${method.returnType}"

            // 🛡️ Protection
            if (!protection.isSafeToPatchMethod(method, classType)) {
                println("[LuckyPatcher] 🛡️ Skipped unsafe int fingerprint $methodKey")
                return@let
            }

            if (methodKey in stats.alreadyPatched) return@let

            val patchCode = if (returnValue <= 1) {
                "const/4 v0, 0x1\nreturn v0"
            } else {
                "const v0, $returnValue\nreturn v0"
            }

            try {
                val backupManager = protection.getBackupManager()
                backupManager.backup(methodKey, method)

                // Safe patch — safeAddInstructions រក mutable method តាមរយៈ context ខាងក្នុង
                val success = SafePatcher.safeAddInstructions(this, method, 0, patchCode, backupManager, methodKey)

                if (success && DexIntegrityChecker.validateMethodAfterPatch(method, classType)) {
                    stats.patched++
                    stats.alreadyPatched.add(methodKey)
                    stats.protectionStats.recordSuccess(classType)
                    println("[LuckyPatcher] ✅ Patched int via fingerprint ${fingerprint.javaClass.simpleName} -> $method returns $returnValue")
                } else {
                    stats.protectionStats.recordFailure()
                }
            } catch (e: Exception) {
                stats.protectionStats.recordFailure()
                println("[LuckyPatcher] ❌ Failed int fingerprint $method: ${e.message}")
            }
        }
    }
}

internal fun BytecodePatchContext.genericScanAndPatch(
    stats: PatchStats,
    booleanIndicators: Set<String>,
    intIndicators: Set<String>,
    intReturnValue: Int = 1,
    booleanReturnTrue: Boolean = true,
    tag: String = "LuckyPatcher",
) {
    val protection = createLuckyPatcherProtection()

    getAllClassesWithStrings().forEach { classDef ->
        val classType = classDef.type

        // 🛡️ System class guard + over-limit guard
        if (MethodValidator.isSystemClass(classType)) {
            // Allow billing-related system classes only if explicitly billing
            val isBillingRelated = classType.contains("billing", ignoreCase = true) ||
                classType.contains("purchase", ignoreCase = true) ||
                classType.contains("license", ignoreCase = true)
            if (!isBillingRelated) {
                stats.protectionStats.recordSkippedSystem()
                return@forEach
            }
        }

        if (protection.getStats().isClassOverLimit(classType, 50)) {
            return@forEach
        }

        classDef.methods.forEach { originalMethod ->
            val impl = originalMethod.implementation ?: return@forEach
            if (AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) || AccessFlags.NATIVE.isSet(originalMethod.accessFlags)) return@forEach

            val methodKey = "${classType}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
            if (methodKey in stats.alreadyPatched) {
                stats.protectionStats.recordSkippedAlreadyPatched()
                return@forEach
            }

            // 🛡️ Safe to patch check
            if (!protection.isSafeToPatchMethod(originalMethod, classType)) {
                return@forEach
            }

            // Safe string collection
            val stringsInMethod = SafeStringCollector.collectStringsSafely(originalMethod)
            if (stringsInMethod.isEmpty()) return@forEach

            val hasBooleanString = stringsInMethod.any { s ->
                booleanIndicators.any { ind -> s.contains(ind, ignoreCase = false) || s.equals(ind, ignoreCase = true) }
            }
            val hasIntString = stringsInMethod.any { s ->
                intIndicators.any { ind -> s.contains(ind, ignoreCase = false) || s.equals(ind, ignoreCase = true) }
            }

            try {
                when {
                    originalMethod.returnType == "Z" && hasBooleanString -> {
                        val code = if (booleanReturnTrue) "const/4 v0, 0x1\nreturn v0" else "const/4 v0, 0x0\nreturn v0"
                        val backupManager = protection.getBackupManager()
                        backupManager.backup(methodKey, originalMethod)

                        val success = SafePatcher.safeAddInstructions(this, originalMethod, 0, code, backupManager, methodKey)
                        if (success && DexIntegrityChecker.validateMethodAfterPatch(originalMethod, classType)) {
                            stats.patched++
                            stats.alreadyPatched.add(methodKey)
                            stats.protectionStats.recordSuccess(classType)
                        } else {
                            stats.protectionStats.recordFailure()
                        }
                    }
                    originalMethod.returnType == "I" && (hasBooleanString || hasIntString) -> {
                        val code = if (intReturnValue <= 1) "const/4 v0, 0x1\nreturn v0" else "const v0, $intReturnValue\nreturn v0"
                        val backupManager = protection.getBackupManager()
                        backupManager.backup(methodKey, originalMethod)

                        val success = SafePatcher.safeAddInstructions(this, originalMethod, 0, code, backupManager, methodKey)
                        if (success && DexIntegrityChecker.validateMethodAfterPatch(originalMethod, classType)) {
                            stats.patched++
                            stats.alreadyPatched.add(methodKey)
                            stats.protectionStats.recordSuccess(classType)
                        } else {
                            stats.protectionStats.recordFailure()
                        }
                    }
                }
            } catch (_: Exception) {
                stats.protectionStats.recordFailure()
            }
        }
    }
}

internal fun BytecodePatchContext.genericBillingBypass(
    stats: PatchStats,
) {
    val protection = createLuckyPatcherProtection()

    val billingIndicators = setOf(
        "is_purchased", "isPurchased", "has_purchase", "is_billing_available",
        "isLicensed", "is_licensed", "check_license", "LICENSED",
        "purchase_state", "is_premium_purchased",
    )

    getAllClassesWithStrings().forEach { classDef ->
        val classType = classDef.type

        // Focus on billing related classes to reduce false positives
        val isBillingClass = classType.contains("billing", ignoreCase = true) ||
            classType.contains("purchase", ignoreCase = true) ||
            classType.contains("license", ignoreCase = true) ||
            classType.contains("vending", ignoreCase = true) ||
            classType.contains("iap", ignoreCase = true)

        if (!isBillingClass) return@forEach

        if (MethodValidator.isSystemClass(classType)) {
            // Allow billing system classes
            val lower = classType.lowercase()
            if (!lower.contains("billing") && !lower.contains("purchase") && !lower.contains("license")) {
                return@forEach
            }
        }

        if (protection.getStats().isClassOverLimit(classType, 50)) return@forEach

        classDef.methods.forEach { originalMethod ->
            val impl = originalMethod.implementation ?: return@forEach
            if (AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) || AccessFlags.NATIVE.isSet(originalMethod.accessFlags)) return@forEach
            if (originalMethod.returnType != "Z" && originalMethod.returnType != "I") return@forEach

            val methodKey = "${classType}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
            if (methodKey in stats.alreadyPatched) return@forEach

            if (!protection.isSafeToPatchMethod(originalMethod, classType, allowSystemBilling = true)) return@forEach

            val stringsInMethod = SafeStringCollector.collectStringsSafely(originalMethod)

            val hasBillingString = stringsInMethod.any { s ->
                billingIndicators.any { ind -> s.contains(ind, ignoreCase = true) }
            }

            val methodNameLower = originalMethod.name.lowercase()
            val isBillingMethodName = methodNameLower.contains("purchas") ||
                methodNameLower.contains("billing") ||
                methodNameLower.contains("licensed") ||
                methodNameLower.contains("license") ||
                methodNameLower.contains("ispro") ||
                methodNameLower.contains("ispremium") ||
                methodNameLower.contains("isvip")

            if (!hasBillingString && !isBillingMethodName) return@forEach

            try {
                val backupManager = protection.getBackupManager()
                backupManager.backup(methodKey, originalMethod)

                val success = when (originalMethod.returnType) {
                    "Z" -> {
                        SafePatcher.safeAddInstructions(this, originalMethod, 0, "const/4 v0, 0x1\nreturn v0", backupManager, methodKey)
                    }
                    "I" -> {
                        val returnVal = if (classType.contains("license", ignoreCase = true)) 0 else 1
                        val code = "const/4 v0, $returnVal\nreturn v0"
                        SafePatcher.safeAddInstructions(this, originalMethod, 0, code, backupManager, methodKey)
                    }
                    else -> false
                }

                if (success && DexIntegrityChecker.validateMethodAfterPatch(originalMethod, classType)) {
                    stats.patched++
                    stats.alreadyPatched.add(methodKey)
                    stats.protectionStats.recordSuccess(classType)
                } else {
                    stats.protectionStats.recordFailure()
                }
            } catch (_: Exception) {
                stats.protectionStats.recordFailure()
            }
        }
    }
}
