package hooman.morphe.patches.simsimi.microg

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import hooman.morphe.patches.simsimi.simsimiCompatibility
import hooman.morphe.patches.simsimi.support.GMS_CORE_PACKAGE
import hooman.morphe.patches.simsimi.support.GMS_VENDOR_GROUP_ID
import hooman.morphe.patches.simsimi.support.simsimiSupportManifestPatch

private const val EXTENSION_PACKAGE_PREFIX = "Lapp/morphe/extension/"

@Suppress("unused")
val gmsCoreBytecodeRedirectPatch = bytecodePatch(
    name = "GmsCore Bytecode Redirect (MicroG RE)",
    description = "Rewire all Google Play Services references to MicroG RE's GmsCore package " +
        "`app.revanced.android.gms`: GMS package-name string constants, account vendor strings, " +
        "c2dm/GMS permissions and provider authorities. Keep service intent ACTIONS literal " +
        "(GmsCore serves them under the original names).",
) {
    compatibleWith(simsimiCompatibility)
    dependsOn(simsimiSupportManifestPatch)

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
            "SimSimi GmsCore: no GMS-related string constants were found. This build does not " +
                "reference Google Play Services, or the layout changed; re-derive the redirect."
        }

        println("[GmsCoreBytecodeRedirect][SimSimi] Rewrote $replaced GMS string constants to $GMS_CORE_PACKAGE")
    }
}

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
