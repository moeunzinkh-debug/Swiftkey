package hooman.morphe.patches.instanttranslate.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import hooman.morphe.patches.instanttranslate.instantTranslateCompatibility
import hooman.morphe.patches.support.InvokeRedirect
import hooman.morphe.patches.support.redirectInvokes
import org.w3c.dom.Element

/** Extension ដែល​ទទួល​ការ​ប្ដូរ​ការ​ហៅ Context.getSharedPreferences។ */
private const val EXTENSION = "Lapp/morphe/extension/instanttranslate/PremiumSupport;"

private const val CONTEXT = "Landroid/content/Context;"
private const val CONTEXT_WRAPPER = "Landroid/content/ContextWrapper;"
private const val SHARED_PREFERENCES = "Landroid/content/SharedPreferences;"

/** កូដ extension ដែល​បាន​ចាក់​បញ្ចូល — មិន​ត្រូវ​ស្កេន​កែ​វា​ទេ។ */
private const val EXTENSION_PACKAGE_PREFIX = "Lapp/morphe/extension/"

/** Flag ដែល​បញ្ជាក់​ក្នុង manifest ថា build នេះ​បាន​អនុវត្ត​បំណះ unlock។ */
private const val FLAG_PREMIUM_UNLOCK = "app.morphe.instanttranslate.PATCH_premium_unlock"

/** ចំនួន​ឈ្មោះ​អតិបរមា​ដែល​បោះពុម្ព​ក្នុង​កំណត់ហេតុ ដើម្បី​ឲ្យ log អាន​បាន។ */
private const val DIAGNOSTIC_LIMIT = 12

/** `const/4` ប្រើ​បាន​តែ register 0..15; លើស​ពី​នោះ​ប្រើ `const/16` (0..255)។ */
private const val CONST4_MAX_REGISTER = 0xF
private const val CONST16_MAX_REGISTER = 0xFF

private val CONSTRUCTOR_NAMES = setOf("<init>", "<clinit>")

/**
 * ដាក់ flag ក្នុង AndroidManifest ដើម្បី​ឲ្យ​ដឹង​ថា build នេះ​បាន​អនុវត្ត​បំណះ unlock។
 * អនុវត្ត​ស្វ័យប្រវត្តិ​ជា dependency។
 */
private val premiumUnlockManifestPatch = resourcePatch(
    description = "Stamps the premium-unlock flag on the manifest so the applied patch is " +
        "reported for this build. Applied automatically with Unlock premium.",
) {
    compatibleWith(instantTranslateCompatibility)

    execute {
        document("AndroidManifest.xml").use { document ->
            val root = document.documentElement
                ?: throw PatchException("Instant Translate: AndroidManifest root element not found.")
            root.applicationElement().setMetaData(FLAG_PREMIUM_UNLOCK, "true")
        }
    }
}

/**
 * បើក premium ដោយ​ផ្នែក client តែ​ម្នាក់ឯង — heuristic បី​ស្រទាប់ ដែល​រាយការណ៍​លទ្ធផល​ក្នុង log:
 *
 *  1. មេតូដ boolean ដែល​ឈ្មោះ​ជា​ច្រក premium (`isPremium`, `isPro`, `hasSubscription`…) →
 *     ត្រឡប់ true ភ្លាម​ដោយ​មិន​រត់​កូដ​ដើម។
 *  2. ការ​អាន field boolean ដែល​ឈ្មោះ​ជា​ច្រក premium (`->premium:Z`) → តម្លៃ​អាន​បាន​ជា true
 *     (កូដ​សរសេរ field នៅ​ដដែល — ការ​អាន​ទេ​ដែល​ត្រូវ​ជំនួស)។
 *  3. រាល់​ការ​ហៅ `Context.getSharedPreferences(...)` ត្រូវ​ប្ដូរ​ទៅ extension ដែល​រាយការណ៍
 *     key premium ថា​បាន​បើក (boolean = true, int/long = 1 ឬ​តម្លៃ​អតិបរមា​សម្រាប់​កាលបរិច្ឆេទ)។
 *
 * អ្វី​ដែល​អនុវត្ត​នៅ​ម៉ាស៊ីន​បម្រើ (entitlement ភ្ជាប់​គណនី, កូតា​បកប្រែ cloud) មិន​អាច
 * បើក​ដោយ​បំណះ​នេះ​ទេ។ បើ​រក​មិន​ឃើញ​ច្រក​ណា​មួយសោះ បំណះ​បរាជ័យ​ជាមួយ​សារ​ពន្យល់ ជាជាង
 * បង្កើត APK ដែល​អ្នក​គិត​ថា​បាន unlock តែ​ខាង​ក្នុង​នៅ​ដដែល។
 */
@Suppress("unused")
val premiumUnlockPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Client-side premium unlock for Instant Translate On Screen. Forces the app's " +
        "premium/subscription gate methods and boolean flags to true and makes its " +
        "SharedPreferences report premium keys as unlocked. Best-effort: the patch log lists " +
        "exactly what was found, and features validated on the server (account-bound " +
        "entitlements, cloud translation quotas) stay locked.",
) {
    compatibleWith(instantTranslateCompatibility)
    dependsOn(premiumUnlockManifestPatch)
    extendWith("extensions/instanttranslate.mpe")

    execute {
        val getters = forcePremiumGetters()
        val fields = forcePremiumFields()

        // រាល់​ការ​ហៅ Context.getSharedPreferences ត្រូវ​ប្ដូរ​ទៅ PremiumSupport ដែល​រាយការណ៍
        // key premium ថា​បាន​បើក។ ContextWrapper ត្រូវ​រាប់​ដែរ ព្រោះ​កូដ​កម្មវិធី​ច្រើន​ហៅ​តាម
        // Activity/Service (ContextWrapper) មិនមែន Context ត្រង់ៗ។
        val preferences = redirectInvokes(
            InvokeRedirect(
                definingClass = CONTEXT,
                name = "getSharedPreferences",
                parameters = listOf("Ljava/lang/String;", "I"),
                returnType = SHARED_PREFERENCES,
                replacementDescriptor = EXTENSION,
                replacementName = "getSharedPreferences",
            ),
            InvokeRedirect(
                definingClass = CONTEXT_WRAPPER,
                name = "getSharedPreferences",
                parameters = listOf("Ljava/lang/String;", "I"),
                returnType = SHARED_PREFERENCES,
                replacementDescriptor = EXTENSION,
                replacementName = "getSharedPreferences",
            ),
        )

        println(
            "[PremiumUnlock][InstantTranslate] premium getters forced: $getters, " +
                "premium field reads forced: $fields, " +
                "SharedPreferences call sites intercepted: $preferences",
        )

        if (getters == 0 && fields == 0 && preferences == 0) {
            throw PatchException(
                "Instant Translate premium unlock: this APK exposes no premium gate the heuristics " +
                    "recognise — no premium-named boolean getter, no premium-named boolean field " +
                    "read and no Context.getSharedPreferences call site. The entitlement is most " +
                    "likely checked inside native/Flutter code or fetched from the server, so this " +
                    "build needs a dedicated fingerprint. Nothing was patched and no patched APK " +
                    "was produced.",
            )
        }
    }
}

/**
 * ស្កេន​គ្រប់ class រក​មេតូដ boolean ដែល​ឈ្មោះ​ជា​ច្រក premium ហើយ​បង្ខំ​ឲ្យ​ត្រឡប់ true។
 *
 * ការ​បញ្ចូល `const/4 v0, 0x1 / return v0` នៅ​ដើមមេតូដ រក្សា​កូដ​ដើម​ទាំង​អស់​នៅ​នឹង​កន្លែង
 * (ក្លាយ​ជា​កូដ​មិន​ដល់) ដូច្នេះ​មិន​ប៉ះ branch/return ណា​មួយទេ។
 *
 * @return ចំនួន​មេតូដ​ដែល​បាន​កែ
 */
context(patchContext: BytecodePatchContext)
private fun forcePremiumGetters(): Int {
    var patched = 0
    val names = mutableListOf<String>()

    patchContext.classDefForEach { classDef: ClassDef ->
        if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) return@classDefForEach
        if (AccessFlags.INTERFACE.isSet(classDef.accessFlags)) return@classDefForEach

        val targets = classDef.methods.filter { method ->
            method.returnType == "Z" &&
                method.implementation != null &&
                method.parameterTypes.size <= 1 &&
                !AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
                !AccessFlags.NATIVE.isSet(method.accessFlags) &&
                method.name !in CONSTRUCTOR_NAMES &&
                PremiumNames.looksPremium(method.name)
        }
        if (targets.isEmpty()) return@classDefForEach

        val mutableClass = patchContext.mutableClassDefBy(classDef)
        targets.forEach { target ->
            val mutable = mutableClass.methods.firstOrNull { it.sameSignatureAs(target) }
                ?: return@forEach
            if (!mutable.forceBooleanTrue()) return@forEach
            patched++
            if (names.size < DIAGNOSTIC_LIMIT) names += shortName(classDef, target)
        }
    }

    if (patched > 0) {
        val more = if (patched > names.size) ", …" else ""
        println(
            "[PremiumUnlock][InstantTranslate] forced premium getters ($patched): " +
                names.joinToString() + more,
        )
    }
    return patched
}

/**
 * ស្កេន​គ្រប់ class រក​ការ​អាន field boolean (`sget-boolean` / `iget-boolean`) ដែល​ឈ្មោះ​ជា
 * ច្រក premium ហើយ​ជំនួស​ការ​អាន​នោះ​ដោយ​តម្លៃ true ដោយ​ផ្ទាល់។
 *
 * កម្មវិធី​ជាច្រើន​ចាប់​យក​ស្ថានភាព premium ទៅ​ជា field ម្ដង (ពេល​ចាប់ផ្ដើម ឬ​ពេល​ទិញ​រួច)
 * ហើយ​អាន​វា​នៅ​កន្លែង​ផ្សេងៗ — ការ​កែ​កន្លែង​អាន​មាន​ប្រសិទ្ធភាព​ជាង​ការ​កែ​កន្លែង​សរសេរ។
 *
 * @return ចំនួន​កន្លែង​អាន​ដែល​បាន​កែ
 */
context(patchContext: BytecodePatchContext)
private fun forcePremiumFields(): Int {
    var patched = 0
    val names = mutableListOf<String>()

    patchContext.classDefForEach { classDef: ClassDef ->
        if (classDef.type.startsWith(EXTENSION_PACKAGE_PREFIX)) return@classDefForEach

        val hits = mutableMapOf<Method, MutableList<Pair<Int, Int>>>()
        classDef.methods.forEach { method: Method ->
            val implementation = method.implementation ?: return@forEach
            implementation.instructions.forEachIndexed { index, instruction ->
                val opcode = instruction.opcode
                if (opcode != Opcode.SGET_BOOLEAN && opcode != Opcode.IGET_BOOLEAN) {
                    return@forEachIndexed
                }
                val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@forEachIndexed
                if (reference.type != "Z" || !PremiumNames.looksPremium(reference.name)) {
                    return@forEachIndexed
                }
                val register = (instruction as? OneRegisterInstruction)?.registerA
                    ?: return@forEachIndexed
                if (register > CONST16_MAX_REGISTER) return@forEachIndexed
                hits.getOrPut(method) { mutableListOf() } += index to register
            }
        }
        if (hits.isEmpty()) return@classDefForEach

        val mutableClass = patchContext.mutableClassDefBy(classDef)
        hits.forEach { (method, locations) ->
            val mutable = mutableClass.methods.firstOrNull { it.sameSignatureAs(method) }
                ?: return@forEach
            // កែ​ពី​ចុង​ទៅ​ដើម ដើម្បី​កុំ​ឲ្យ​លេខ​រៀង​ខ្លួន​មុនៗ​រើ​ទីតាំង។
            locations.sortedByDescending { it.first }.forEach { (index, register) ->
                mutable.replaceInstruction(index, if (register <= CONST4_MAX_REGISTER) {
                    "const/4 v$register, 0x1"
                } else {
                    "const/16 v$register, 0x1"
                })
                patched++
            }
            if (names.size < DIAGNOSTIC_LIMIT) names += shortName(classDef, method)
        }
    }

    if (patched > 0) {
        val more = if (names.size >= DIAGNOSTIC_LIMIT) ", …" else ""
        println(
            "[PremiumUnlock][InstantTranslate] forced premium field reads ($patched in " +
                "${names.size} method(s)): " + names.joinToString() + more,
        )
    }
    return patched
}

/** តើ [other] មាន​ហត្ថលេខា​ដូច​មេតូដ​នេះ​ឬ​ទេ (ឈ្មោះ + ប្រភេទ​ត្រឡប់ + ប៉ារ៉ាម៉ែត្រ)។ */
private fun MutableMethod.sameSignatureAs(other: Method): Boolean =
    name == other.name &&
        returnType == other.returnType &&
        parameterTypes.size == other.parameterTypes.size &&
        parameterTypes.zip(other.parameterTypes).all { (a, b) -> a.toString() == b.toString() }

/**
 * បង្ខំ​មេតូដ boolean ឲ្យ​ត្រឡប់ true ដោយ​បន្ថែម​ការ​ត្រឡប់​នៅ​ដើមមេតូដ។
 *
 * មេតូដ​ដែល​ត្រឡប់ boolean ត្រូវ​មាន register យ៉ាង​តិច ១ ជានិច្ច (តម្លៃ​ត្រឡប់) ដូច្នេះ
 * registerCount < 1 មានន័យ​ថា​មេតូដ​នេះ​មិន​អាច​កែ​បាន — រំលង​វា។
 *
 * @return true បើ​កែ​បាន
 */
private fun MutableMethod.forceBooleanTrue(): Boolean {
    val implementation = implementation ?: return false
    if (implementation.registerCount < 1) return false
    addInstructions(0, "const/4 v0, 0x1\nreturn v0")
    return true
}

/** `Lcom/foo/Bar;->isPremium()Z` → `Bar.isPremium` សម្រាប់​កំណត់ហេតុ។ */
private fun shortName(classDef: ClassDef, method: Method): String =
    classDef.type.substringAfterLast('/').removeSuffix(";") + "." + method.name

// ---------------------------------------------------------------------------
// Manifest helpers (អត់​ខ្ទង់ namespace prefix ដូចបំណះ SwiftKey)
// ---------------------------------------------------------------------------

private fun Element.applicationElement(): Element {
    val application = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }
        .firstOrNull { it.tagName.substringAfterLast(':') == "application" }
    return application ?: throw PatchException(
        "Instant Translate: expected an application element in AndroidManifest.xml.",
    )
}

private fun Element.setMetaData(name: String, value: String) {
    val existing = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }
        .filter { it.tagName.substringAfterLast(':') == "meta-data" }
        .filter { it.getAttribute("android:name") == name }
    val item = existing.singleOrNull()
        ?: ownerDocument.createElement("meta-data").also { appendChild(it) }
    item.setAttribute("android:name", name)
    item.setAttribute("android:value", value)
}
