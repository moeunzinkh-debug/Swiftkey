package hooman.morphe.patches.swiftkey.microg

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import hooman.morphe.patches.swiftkey.support.GMS_CORE_PACKAGE
import hooman.morphe.patches.swiftkey.support.GMS_VENDOR_GROUP_ID
import hooman.morphe.patches.swiftkey.support.swiftKeySupportManifestPatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility

private const val EXTENSION_PACKAGE_PREFIX = "Lapp/morphe/extension/"

/**
 * GmsCore Bytecode Redirect (MicroG RE 7.1.2)
 *
 * ប្ដូរ​រាល់​ខ្សែអក្សរ​ក្នុង DEX ដែល​បញ្ជូន​កម្មវិធី​ទៅកាន់ Google Play Services
 * ("com.google.android.gms", គណនី vendor "com.google", សិទ្ធិ c2dm/GMS និង
 * provider authorities) ឲ្យ​ទៅ​កាន់​ GmsCore (កញ្ចប់ app.revanced.android.gms របស់
 * MicroG RE / ReVanced GmsCore) វិញ។
 *
 * បច្ចេកទេស​ដូច​គ្នា​នឹង ReVanced GmsCore support៖
 *  - ប្តូរ​តែ​ឈ្មោះ​ដែល​ផ្គូផ្គង​ពិតប្រាកដ (exact) តាម​បញ្ជី​ស ដើម្បី​កុំ​ឲ្យ​ប៉ះ
 *    ខ្សែអក្សរ​ដទៃ (ឧ. ឈ្មោះ​ថ្នាក់​ពេញ​របស់ Microsoft ឬ កញ្ចប់ Google Speech)
 *  - content:// URIs ប្តូរ​ត្រឹម​ authority ដែល GmsCore​ផ្តល់​
 *  - service actions នៅ​ដដែល (GmsCore​ទទួល​តាម​ឈ្មោះ​ពិត)
 *  - រំលង​កូដ extension ផ្ទាល់ខ្លួន (ការពារ​ការ​ប្តូរ​ខ្សែអក្សរ app.revanced
 *    ដែល​យើង​ដាក់​ដោយ​ចេតនា)
 */
@Suppress("unused")
val gmsCoreBytecodeRedirectPatch = bytecodePatch(
    name = "GmsCore Bytecode Redirect (MicroG RE 7.1.2)",
    description = "Rewires the app's Google Play Services references (GMS package name, account " +
        "vendor, c2dm/GMS permissions and provider authorities) to MicroG RE's GmsCore package " +
        "app.revanced.android.gms. Service intent actions are kept literal because GmsCore serves " +
        "them under the original names. Enable together with the other GmsCore patches.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(swiftKeySupportManifestPatch)

    execute {
        fun transform(referencedString: String): String? = when {
            referencedString == "com.google.android.gms" -> GMS_CORE_PACKAGE
            referencedString == "com.google" -> GMS_VENDOR_GROUP_ID

            referencedString in GmsConstants.PERMISSIONS ||
                referencedString in GmsConstants.ACTIONS ||
                referencedString in GmsConstants.AUTHORITIES ->
                referencedString.replace("com.google", GMS_VENDOR_GROUP_ID)

            referencedString == "subscribedfeeds" -> "$GMS_VENDOR_GROUP_ID.subscribedfeeds"

            referencedString.startsWith("content://") -> transformContentUri(referencedString)

            else -> null
        }

        var replaced = 0

        getAllClassesWithStrings().forEach { classDef ->
            if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) return@forEach

            val mutableClass = mutableClassDefBy(classDef)

            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach

                val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                    candidate.name == method.name &&
                        candidate.returnType == method.returnType &&
                        candidate.parameterTypes.size == method.parameterTypes.size &&
                        candidate.parameterTypes.zip(method.parameterTypes).all { (a, b) ->
                            a.toString() == b.toString()
                        }
                } ?: return@forEach

                // ប្តូរ​ពី​ក្រោយ​ទៅ​មុខ ដើម្បី​រក្សា​លេខ​រៀង​សេចក្តី​ណែនាំ​មុនៗ។
                implementation.instructions.forEachIndexed { index, instruction ->
                    val stringReference = when {
                        instruction is Instruction21c && instruction.reference is StringReference ->
                            (instruction.reference as StringReference).string
                        instruction is Instruction31c && instruction.reference is StringReference ->
                            (instruction.reference as StringReference).string
                        else -> null
                    } ?: return@forEachIndexed

                    val transformed = transform(stringReference) ?: return@forEachIndexed

                    val register = when (instruction) {
                        is Instruction21c -> instruction.registerA
                        is Instruction31c -> instruction.registerA
                        else -> return@forEachIndexed
                    }
                    val newReference = ImmutableStringReference(transformed)
                    mutableMethod.replaceInstruction(
                        index,
                        when (instruction.opcode) {
                            Opcode.CONST_STRING_JUMBO ->
                                BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, register, newReference)
                            else ->
                                BuilderInstruction21c(Opcode.CONST_STRING, register, newReference)
                        },
                    )
                    replaced++
                }
            }
        }

        check(replaced > 0) {
            "SwiftKey GmsCore: no GMS-related string constants were found. This build does not " +
                "reference Google Play Services, or the layout changed; re-derive the redirect."
        }

        println("[GmsCoreBytecodeRedirect] Rewrote $replaced GMS string constants to $GMS_CORE_PACKAGE")
    }
}

/** ប្តូរ​តែ​បុព្វបទ content:// នៃ authorities ដែល GmsCore​ផ្តល់​សេវា​ជំនួស។ */
private fun transformContentUri(uri: String): String? {
    GmsConstants.AUTHORITIES.forEach { authority ->
        val prefix = "content://$authority"
        if (uri.startsWith(prefix)) {
            return uri.replace(
                prefix,
                "content://${authority.replace("com.google", GMS_VENDOR_GROUP_ID)}",
            )
        }
    }

    val subFeeds = "content://subscribedfeeds"
    return if (uri.startsWith(subFeeds)) {
        uri.replace(subFeeds, "content://$GMS_VENDOR_GROUP_ID.subscribedfeeds")
    } else {
        null
    }
}
