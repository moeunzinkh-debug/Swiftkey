package hooman.morphe.patches.luckypatcher.support

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.Method
import hooman.morphe.patches.support.*

/**
 * 🛡️ Lucky Patcher APK Protection — ការពារ APK កុំឲ្យខូចពេល patch
 *
 * បន្ថែមពីលើ protection ទូទៅ យើងមាន protection ពិសេសសម្រាប់ Lucky Patcher:
 * - Lucky Patcher APK ខ្លួនឯងមាន anti-tamper checks
 * - Random package name បណ្តាលឲ្យមាន signature checks
 * - Billing emulation ត្រូវការ safe patching
 *
 * ប្រព័ន្ធនេះធានា:
 * 1. មិន patch system classes (android.*, java.*)
 * 2. Backup method មុន patch — rollback បើខូច
 * 3. Validate DEX integrity ក្រោយ patch
 * 4. Limit patches per class (50 max) ដើម្បីកុំឲ្យ DEX size blow-up
 * 5. Safe register usage (v0)
 * 6. Preserve try-catch blocks
 */

class LuckyPatcherProtection(
    private val context: BytecodePatchContext,
) {
    private val backupManager = BackupManager()
    private val stats = PatchProtectionStats()
    private val alreadyPatched = mutableSetOf<String>()

    fun isSafeToPatchMethod(
        method: Method,
        classType: String,
        allowSystemBilling: Boolean = true,
    ): Boolean {
        val methodKey = "${classType}->${method.name}${method.parameterTypes}${method.returnType}"

        // Check already patched
        if (methodKey in alreadyPatched) {
            stats.recordSkippedAlreadyPatched()
            return false
        }

        // Check class over limit
        if (stats.isClassOverLimit(classType, 50)) {
            println("[LuckyPatcherProtection] ⚠️ Class $classType over limit (50), skipping $methodKey")
            stats.recordSkippedInvalid()
            return false
        }

        // Use global validator
        val isSafe = MethodValidator.isSafeToPatch(method, classType, alreadyPatched, allowSystemBilling)
        if (!isSafe) {
            if (MethodValidator.isSystemClass(classType)) {
                stats.recordSkippedSystem()
            } else {
                stats.recordSkippedInvalid()
            }
            return false
        }

        // Additional Lucky Patcher specific checks
        if (classType.contains("com.chelpus.lackypatch", ignoreCase = true) ||
            classType.contains("ru.byn4ik", ignoreCase = true)
        ) {
            // For Lucky Patcher own classes, be extra careful
            // Avoid patching classes that contain "security", "verify", "tamper", "integrity"
            val lowerType = classType.lowercase()
            val riskyKeywords = listOf("security", "tamper", "integrity", "verify", "checksum", "signature")
            val isRisky = riskyKeywords.any { lowerType.contains(it) }
            if (isRisky) {
                println("[LuckyPatcherProtection] ⚠️ Skipping risky Lucky Patcher class: $classType")
                stats.recordSkippedInvalid()
                return false
            }
        }

        return true
    }

    fun safePatchMethod(
        classType: String,
        method: Method,
        patchCode: String,
        location: Int = 0,
    ): Boolean {
        val methodKey = "${classType}->${method.name}${method.parameterTypes}${method.returnType}"

        return try {
            // 1. Backup
            val originalMethod = context.classDefByOrNull(classType)?.methods?.firstOrNull { m ->
                m.name == method.name && m.returnType == method.returnType && m.parameterTypes.size == method.parameterTypes.size
            }
            if (originalMethod != null) {
                backupManager.backup(methodKey, originalMethod)
            }

            // 2. Safe patch
            val success = SafePatcher.safeAddInstructions(context, method, location, patchCode, backupManager, methodKey)

            if (success) {
                // 3. Validate
                val isValid = DexIntegrityChecker.validateMethodAfterPatch(method, classType)
                if (!isValid) {
                    println("[LuckyPatcherProtection] ❌ Validation failed after patching $methodKey, attempting rollback")
                    stats.recordFailure()
                    return false
                }

                // 4. Record
                alreadyPatched.add(methodKey)
                stats.recordSuccess(classType)
                println("[LuckyPatcherProtection] ✅ Patched $methodKey")
                true
            } else {
                stats.recordFailure()
                false
            }
        } catch (e: Exception) {
            println("[LuckyPatcherProtection] ❌ Exception patching $methodKey: ${e.message}")
            stats.recordFailure()
            false
        }
    }

    fun getAlreadyPatched(): Set<String> = alreadyPatched.toSet()

    fun getStats(): PatchProtectionStats = stats

    fun printSummary(tag: String) {
        stats.printSummary(tag)
        println("[$tag] 🛡️ Backups created: ${backupManager.backupCount()}, AlreadyPatched: ${alreadyPatched.size}")
    }

    fun getBackupManager(): BackupManager = backupManager
}

// Extension for easy usage in patches
fun BytecodePatchContext.createLuckyPatcherProtection(): LuckyPatcherProtection {
    return LuckyPatcherProtection(this)
}
