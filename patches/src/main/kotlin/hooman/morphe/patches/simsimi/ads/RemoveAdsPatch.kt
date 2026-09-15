package hooman.morphe.patches.simsimi.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import hooman.morphe.patches.simsimi.simsimiCompatibility

/**
 * Remove ads
 *
 * Removes banners, interstitials, rewarded ads, native ads from SimSimi.
 * Strategy:
 *  1. Nuke known ad SDK packages (AdMob, Unity Ads, AppLovin, IronSource, Facebook Audience, MoPub, etc)
 *     by forcing all their methods to return early (void -> return-void, boolean -> false, object -> null, int -> 0).
 *  2. Generic scan: any app method that contains strong ad-related string constants
 *     (e.g. "Ad failed to load", "AdMob", "InterstitialAd", "RewardedAd") is neutralized based on its return type.
 *  3. Soft-fail: if nothing found, log warning instead of crashing.
 *
 * Server-validated ad configs remain, but no ad will be requested or shown client-side.
 */
@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes ads, banners, interstitials, rewarded ads from SimSimi. Blocks AdMob, Unity Ads, AppLovin, IronSource and other ad SDKs client-side.",
) {
    compatibleWith(simsimiCompatibility)

    execute {
        var patched = 0

        // ---- 1. Known ad SDK package prefixes --------------------------------
        val adPackagePrefixes = listOf(
            "Lcom/google/android/gms/ads/",
            "Lcom/google/android/gms/internal/ads/",
            "Lcom/google/ads/",
            "Lcom/google/android/gms/ads/",
            "Lcom/unity3d/ads/",
            "Lcom/applovin/",
            "Lcom/applovin/impl/",
            "Lcom/ironsource/",
            "Lcom/facebook/ads/",
            "Lcom/mopub/",
            "Lcom/chartboost/sdk/",
            "Lcom/vungle/",
            "Lcom/inmobi/",
            "Lcom/appodeal/",
            "Lcom/adcolony/",
            "Lcom/startapp/",
            "Lcom/fyber/",
            "Lcom/tapjoy/",
            "Lcom/unity3d/services/ads/",
            "Lcom/bytedance/sdk/openadsdk/",
            "Lcom/pangle/",
        )

        // Patch all methods in ad SDK classes
        classDefForEach { classDef ->
            val type = classDef.type
            if (adPackagePrefixes.none { type.startsWith(it) }) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach
                if (AccessFlags.ABSTRACT.isSet(method.accessFlags) || AccessFlags.NATIVE.isSet(method.accessFlags)) return@forEach

                val isStatic = AccessFlags.STATIC.isSet(method.accessFlags)
                val hasParams = method.parameterTypes.isNotEmpty()
                val returnType = method.returnType

                try {
                    when {
                        returnType == "V" -> {
                            method.addInstructions(0, "return-void")
                            patched++
                        }
                        returnType == "Z" -> {
                            // ប្រើ v0 ជានិច្ច — កុំ overwrite p0 (this) ក្នុង instance method
                            val code = "const/4 v0, 0x0\nreturn v0"
                            method.addInstructions(0, code)
                            patched++
                        }
                        returnType == "I" || returnType == "F" -> {
                            val code = "const/4 v0, 0x0\nreturn v0"
                            method.addInstructions(0, code)
                            patched++
                        }
                        returnType.startsWith("L") || returnType.startsWith("[") -> {
                            val code = "const/4 v0, 0x0\nreturn-object v0"
                            method.addInstructions(0, code)
                            patched++
                        }
                    }
                } catch (_: Exception) {
                    // ignore individual failures
                }
            }
        }

        // ---- 2. Generic scan for app methods containing strong ad strings ----
        val strongAdIndicators = setOf(
            "AdMob",
            "admob",
            "com.google.android.gms.ads",
            "AdView",
            "InterstitialAd",
            "RewardedAd",
            "NativeAd",
            "AdLoader",
            "AdRequest",
            "Ad failed to load",
            "Ad loaded",
            "Ad closed",
            "Ad opened",
            "show_interstitial",
            "show_rewarded",
            "loadAd",
            "showAd",
            "adsense",
            "doubleclick.net",
            "googlesyndication",
            "facebook_audience_network",
            "applovin",
            "ironsource",
            "unity_ads",
            "mopub",
            "chartboost",
            "vungle",
            "inmobi",
            "appodeal",
            "adcolony",
            "startapp",
            "tapjoy",
            "pangle",
            "openadsdk",
            "banner_ad",
            "interstitial_ad",
            "rewarded_ad",
            "native_ad",
        )

        // To avoid over-matching, we require at least one strong indicator and
        // the method's class is not already an ad SDK class (already patched above)
        // and return type is suitable for neutralizing.

        val alreadyPatchedSignatures = mutableSetOf<String>()

        getAllClassesWithStrings().forEach { classDef ->
            if (adPackagePrefixes.any { classDef.type.startsWith(it) }) return@forEach
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@forEach

            // classDef ពី getAllClassesWithStrings() អាចជា immutable — យក mutable version
            // ដើម្បីអាចកែ implementation បាន (addInstructions ត្រូវការ MutableMethod)
            val mutableClass = try {
                mutableClassDefBy(classDef)
            } catch (_: Exception) {
                return@forEach
            }

            classDef.methods.forEach { originalMethod ->
                val impl = originalMethod.implementation ?: return@forEach
                if (AccessFlags.ABSTRACT.isSet(originalMethod.accessFlags) || AccessFlags.NATIVE.isSet(originalMethod.accessFlags)) return@forEach

                val methodSig = "${classDef.type}->${originalMethod.name}${originalMethod.parameterTypes}${originalMethod.returnType}"
                if (methodSig in alreadyPatchedSignatures) return@forEach

                // Collect string constants in method
                val stringsInMethod = mutableSetOf<String>()
                impl.instructions.forEach { insn ->
                    try {
                        val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference
                        ref?.string?.let { stringsInMethod.add(it) }
                    } catch (_: Exception) {}
                    try {
                        val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference
                        ref?.string?.let { stringsInMethod.add(it) }
                    } catch (_: Exception) {}
                }

                if (stringsInMethod.isEmpty()) return@forEach

                val hasStrongAdString = stringsInMethod.any { s ->
                    strongAdIndicators.any { indicator -> s.contains(indicator) }
                }

                if (!hasStrongAdString) return@forEach

                // Heuristic: only patch methods that look like ad show/load/isAvailable
                val returnType = originalMethod.returnType
                val isStatic = AccessFlags.STATIC.isSet(originalMethod.accessFlags)
                val hasParams = originalMethod.parameterTypes.isNotEmpty()

                // រក mutable method ត្រូវគ្នា
                val mutableMethod = mutableClass.methods.firstOrNull { candidate ->
                    candidate.name == originalMethod.name &&
                        candidate.returnType == originalMethod.returnType &&
                        candidate.parameterTypes.size == originalMethod.parameterTypes.size &&
                        candidate.parameterTypes.zip(originalMethod.parameterTypes).all { (a, b) -> a.toString() == b.toString() }
                } ?: return@forEach

                try {
                    when {
                        returnType == "V" -> {
                            // Void methods that load/show ads -> no-op
                            mutableMethod.addInstructions(0, "return-void")
                            patched++
                            alreadyPatchedSignatures.add(methodSig)
                        }
                        returnType == "Z" -> {
                            // Boolean methods like isAdLoaded, shouldShowAd -> false
                            // ប្រើ v0 ជានិច្ចដើម្បីជៀសវាង overwrite this (p0) ក្នុង instance method
                            val code = "const/4 v0, 0x0\nreturn v0"
                            mutableMethod.addInstructions(0, code)
                            patched++
                            alreadyPatchedSignatures.add(methodSig)
                        }
                        returnType.startsWith("L") || returnType.startsWith("[") -> {
                            // Object returning ad -> null, but be conservative: only if method name suggests ad
                            val lowerName = originalMethod.name.lowercase()
                            if (lowerName.contains("ad") || lowerName.contains("banner") || lowerName.contains("interstitial") || lowerName.contains("reward")) {
                                val code = "const/4 v0, 0x0\nreturn-object v0"
                                mutableMethod.addInstructions(0, code)
                                patched++
                                alreadyPatchedSignatures.add(methodSig)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // ---- 3. Result --------------------------------------------------------
        if (patched == 0) {
            println(
                "[RemoveAds] WARNING: No ad SDK methods were found to patch. " +
                    "The app's ad integration may have changed; ad removal had nothing to patch.",
            )
        } else {
            println("[RemoveAds] Patched $patched ad-related methods to block ads")
        }
    }
}
