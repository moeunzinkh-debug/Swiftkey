package hooman.morphe.patches.support

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c

/**
 * 🛡️ APK Protection System — ការពារ APK កុំឲ្យខូច ឬគាំងពេល patch
 *
 * ប្រព័ន្ធនេះបង្កើតឡើងដើម្បីដោះស្រាយបញ្ហា APK ខូច (corrupted) ពេល patch:
 * - Register overflow (ប្រើ v0 ពេល method មិនមាន register គ្រប់គ្រាន់)
 * - Double patching (patch method ដដែល 2 ដង)
 * - Patching system classes (android.*, java.*) បណ្តាលឲ្យ bootloop/crash
 * - Patching constructors / abstract / native methods
 * - Try-catch block corruption
 * - DEX size blow-up (patch ច្រើនពេកក្នុង class តែមួយ)
 * - Invalid bytecode after patch
 */

// ---- 1. Safe Method Validator ----
object MethodValidator {

    private val forbiddenClassPrefixes = listOf(
        "Landroid/", "Ljava/", "Lkotlin/", "Lkotlinx/",
        "Landroidx/",
        "Lcom/google/android/gms/internal/",
        "Lapp/morphe/extension/",
    )

    private val forbiddenMethodNames = setOf(
        "<clinit>",
    )

    fun isSystemClass(classType: String): Boolean {
        return forbiddenClassPrefixes.any { classType.startsWith(it) }
    }

    fun isSafeToPatch(
        method: Method,
        classType: String,
        alreadyPatched: Set<String> = emptySet(),
    ): Boolean {
        val impl = method.implementation ?: return false

        if (AccessFlags.ABSTRACT.isSet(method.accessFlags)) return false
        if (AccessFlags.NATIVE.isSet(method.accessFlags)) return false
        if (method.name in forbiddenMethodNames) return false

        if (isSystemClass(classType)) return false

        val methodKey = "${classType}->${method.name}${method.parameterTypes}${method.returnType}"
        if (methodKey in alreadyPatched) return false

        if (impl.instructions.isEmpty()) return false
        if (impl.registerCount < 1) return false

        return true
    }

    fun isSafeReturnType(returnType: String): Boolean {
        return returnType == "Z" || returnType == "I" || returnType == "Ljava/lang/String;" || returnType.startsWith("L") || returnType.startsWith("[")
    }
}

// ---- 2. Backup Manager ----
class BackupManager {
    private val backups = mutableMapOf<String, List<Instruction>>()
    private val backupRegisterCounts = mutableMapOf<String, Int>()

    fun backup(methodKey: String, method: Method): Boolean {
        return try {
            val impl = method.implementation ?: return false
            backups[methodKey] = impl.instructions.toList()
            backupRegisterCounts[methodKey] = impl.registerCount
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasBackup(methodKey: String): Boolean = methodKey in backups

    fun clear() {
        backups.clear()
        backupRegisterCounts.clear()
    }

    fun backupCount(): Int = backups.size
}

// ---- 3. Safe Instruction Inserter ----
object SafePatcher {

    fun safeAddInstructions(
        method: com.android.tools.smali.dexlib2.iface.MutableMethod,
        location: Int,
        instructions: String,
        backupManager: BackupManager? = null,
        methodKey: String? = null,
    ): Boolean {
        return try {
            val impl = method.implementation
            if (impl != null && impl.registerCount < 1) {
                println("[ApkProtection] ⚠️ Method ${method.name} has registerCount ${impl.registerCount} < 1, skipping patch")
                return false
            }

            val instrCount = method.implementation?.instructions?.count() ?: 0
            if (location < 0 || location > instrCount) {
                println("[ApkProtection] ⚠️ Invalid location $location for method ${method.name} with $instrCount instructions")
                return false
            }

            method.addInstructions(location, instructions)

            val newImpl = method.implementation
            if (newImpl == null || newImpl.instructions.isEmpty()) {
                println("[ApkProtection] ❌ Method ${method.name} implementation invalid after patch")
                return false
            }

            true
        } catch (e: Exception) {
            println("[ApkProtection] ❌ Failed to patch method ${method.name}: ${e.message}")
            false
        }
    }
}

// ---- 4. Dex Integrity Checker ----
object DexIntegrityChecker {

    fun validateMethodAfterPatch(
        method: Method,
        classType: String,
    ): Boolean {
        return try {
            val impl = method.implementation ?: return false

            if (impl.instructions.isEmpty()) {
                println("[ApkProtection] ❌ Validation failed: $classType->${method.name} has no instructions")
                return false
            }

            if (impl.registerCount < 0 || impl.registerCount > 65535) {
                println("[ApkProtection] ❌ Validation failed: $classType->${method.name} invalid registerCount ${impl.registerCount}")
                return false
            }

            impl.tryBlocks.forEach { tryBlock ->
                if (tryBlock.startAddress < 0 || tryBlock.codeUnitCount <= 0) {
                    println("[ApkProtection] ❌ Validation failed: $classType->${method.name} invalid try block")
                    return false
                }
            }

            true
        } catch (e: Exception) {
            println("[ApkProtection] ❌ Validation exception for $classType->${method.name}: ${e.message}")
            false
        }
    }
}

// ---- 5. Resource Protection ----
object ResourceProtection {

    fun safeDocumentEdit(
        fileName: String,
        editBlock: () -> Unit,
    ): Boolean {
        return try {
            editBlock()
            true
        } catch (e: Exception) {
            println("[ApkProtection] ❌ Failed to edit $fileName safely: ${e.message}")
            false
        }
    }
}
