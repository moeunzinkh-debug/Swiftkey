package hooman.morphe.patches.swiftkey.support

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import org.w3c.dom.Element

/** កញ្ចប់ GmsCore ដែល​បាន​ដំឡើង​ជំនួស Google Play Services (MicroG RE / ReVanced GmsCore)។ */
const val GMS_CORE_PACKAGE = "app.revanced.android.gms"
const val GMS_VENDOR_GROUP_ID = "app.revanced"

/** សិទ្ធិ​គណនី​ដែល GmsCore ត្រូវការ។ */
const val PERMISSION_GET_ACCOUNTS = "android.permission.GET_ACCOUNTS"
const val PERMISSION_GMS_EXTENDED_ACCESS = "app.revanced.gms.EXTENDED_ACCESS"

/** កញ្ចប់​ដើម​របស់ SwiftKey — ប្រើ​នៅ​ពេល​បង្កើត​ក្លូន។ */
const val SWIFTKEY_PACKAGE = "com.touchtype.swiftkey"

/**
 * តម្លៃ​ហត្ថលេខា​ដើម (SHA-1 hex) សម្រាប់ SPOOFED_PACKAGE_SIGNATURE។ បំពេញ​ដោយ​បំណះ
 * "GmsCore support" តាម​រយៈ​ option របស់​អ្នកប្រើ (ត្រូវការ​តែ​ពេល​ដាក់​ជា​ក្លូន)។
 */
internal var spoofedSignatureProvider: () -> String? = { null }

/**
 * បំណះ​ធនធាន​ផ្ទៃក្នុង (អនុវត្ត​ស្វ័យប្រវត្តិ​ជា​dependency) ដែល​រៀបចំ AndroidManifest
 * សម្រាប់​ទាំង​មីក្រូហ្វូន និង GmsCore៖
 *
 *  1. បើក​ការ​មើលឃើញ​កញ្ចប់ (Android 11+ package visibility)៖
 *     - &lt;queries&gt; GmsCore (app.revanced.android.gms)
 *     - &lt;queries&gt; intent "android.speech.RecognitionService" ដើម្បី​ឲ្យ
 *       MicSupport រក​ម៉ាស៊ីន​សម្គាល់​សំឡេង​ឯករាជ្យ (Kõnele/Vosk ...) ឃើញ
 *  2. បន្ថែម​សិទ្ធិ GET_ACCOUNTS + EXTENDED_ACCESS (microG គណនី)
 *  3. ប្ដូរ​ឈ្មោះ​សិទ្ធិ c2dm (push) ពី com.google.* ទៅ app.revanced.* ឲ្យ
 *     ស្រប​នឹង GmsCore
 *  4. ពេល​បាន​ក្លូន (packageName ខុស​ពី​ដើម)៖ បន្ថែម meta-data
 *     SPOOFED_PACKAGE_NAME/SPOOFED_PACKAGE_SIGNATURE ឲ្យ GmsCore ស្គាល់​អត្តសញ្ញាណ​ដើម
 */
internal val swiftKeySupportManifestPatch = resourcePatch(
    description = "Prepares the AndroidManifest for the microphone fix and GmsCore support " +
        "(package visibility queries, account permissions, c2dm rename, clone spoof metadata). " +
        "Applied automatically with Fix microphone voice input and GmsCore support.",
) {
    compatibleWith(swiftKeyCompatibility)

    fun Element.androidName(): String = getAttribute("android:name")

    fun Element.ensureChildElement(tag: String, configure: Element.() -> Unit = {}): Element {
        val existing = getElementsByTagName(tag)
        val child = if (existing.length > 0) {
            existing.item(0) as Element
        } else {
            ownerDocument.createElement(tag).also { appendChild(it) }
        }
        child.configure()
        return child
    }

    execute {
        document("AndroidManifest.xml").use { document ->
            val root = document.documentElement
                ?: throw PatchException("SwiftKey: AndroidManifest root element not found.")

            // ---- 1. <queries> -------------------------------------------------
            val queries = root.ensureChildElement("queries")

            val hasGmsQuery = (0 until queries.childNodes.length).any { i ->
                val node = queries.childNodes.item(i) as? Element
                node?.tagName == "package" && node.androidName() == GMS_CORE_PACKAGE
            }
            if (!hasGmsQuery) {
                document.createElement("package").also { pkg ->
                    pkg.setAttribute("android:name", GMS_CORE_PACKAGE)
                    queries.appendChild(pkg)
                }
            }

            val speechQueryAction = "android.speech.RecognitionService"
            val hasSpeechQuery = (0 until queries.childNodes.length).any { i ->
                val intent = queries.childNodes.item(i) as? Element
                intent?.tagName == "intent" &&
                    (0 until intent.childNodes.length).any { j ->
                        val action = intent.childNodes.item(j) as? Element
                        action?.tagName == "action" && action.androidName() == speechQueryAction
                    }
            }
            if (!hasSpeechQuery) {
                document.createElement("intent").also { intent ->
                    document.createElement("action").also { action ->
                        action.setAttribute("android:name", speechQueryAction)
                        intent.appendChild(action)
                    }
                    queries.appendChild(intent)
                }
            }

            // ---- 2. uses-permission ------------------------------------------
            fun hasUsePermission(name: String): Boolean =
                root.getElementsByTagName("uses-permission").let { nodes ->
                    (0 until nodes.length).any {
                        (nodes.item(it) as? Element)?.androidName() == name
                    }
                }

            fun addUsePermission(name: String) {
                if (hasUsePermission(name)) return
                root.appendChild(document.createElement("uses-permission").also {
                    it.setAttribute("android:name", name)
                })
            }

            addUsePermission(PERMISSION_GET_ACCOUNTS)
            addUsePermission(PERMISSION_GMS_EXTENDED_ACCESS)

            // ---- 3. c2dm permission rename -----------------------------------
            // GmsCore ប្រកាស​សិទ្ធិ push ក្រោម vendor app.revanced — រាល់​ឈ្មោះ
            // សិទ្ធិ com.google.android.c2dm.* ត្រូវ​ធ្វើ​តាម។ សកម្មភាព intent
            // ("com.google.android.c2dm.intent.*") នៅ​ដដែល (GmsCore បម្រើ​តាម​ឈ្មោះ​ពិត)។
            fun rewriteC2dm(name: String): String? =
                if (name.startsWith("com.google.android.c2dm")) {
                    name.replace("com.google", GMS_VENDOR_GROUP_ID)
                } else {
                    null
                }

            sequenceOf("permission", "uses-permission", "uses-permission-sdk-23").forEach { tag ->
                val nodes = root.getElementsByTagName(tag)
                (0 until nodes.length).forEach { i ->
                    val element = nodes.item(i) as? Element ?: return@forEach
                    rewriteC2dm(element.androidName())?.let { element.setAttribute("android:name", it) }
                }
            }

            val permissionAttributes = arrayOf(
                "android:permission",
                "android:readPermission",
                "android:writePermission",
            )
            val all = root.getElementsByTagName("*")
            (0 until all.length).forEach { i ->
                val element = all.item(i) as? Element ?: return@forEach
                permissionAttributes.forEach { attribute ->
                    val current = element.getAttribute(attribute)
                    if (!current.isNullOrBlank()) {
                        rewriteC2dm(current)?.let { element.setAttribute(attribute, it) }
                    }
                }
            }

            // ---- 4. Clone spoof metadata --------------------------------------
            // អនុវត្ត​តែ​ពេល​កញ្ចប់​ត្រូវ​បាន​ប្តូរ​ឈ្មោះ​ (ក្លូន)។ កម្មវិធី​កំណត់​
            // packageName ពិត​ក្នុង​ដំណាក់កាល finalize។
        }
    }

    finalize {
        val currentPackage = packageMetadata.packageName
        if (currentPackage == SWIFTKEY_PACKAGE) return@finalize

        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application")
                .let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }
                .singleOrNull()
                ?: throw PatchException("SwiftKey: expected exactly one application element.")

            val signature = spoofedSignatureProvider()?.takeIf { it.isNotBlank() }
                ?: throw PatchException(
                    "SwiftKey: this build is cloned/renamed ($currentPackage), so GmsCore needs the " +
                        "original app signing SHA-1 to spoof its identity. Set the \"Original signing " +
                        "certificate SHA-1\" option on the \"GmsCore support (MicroG RE)\" patch " +
                        "(read it from the unmodified APK with: apksigner verify --print-cert " +
                        "SwiftKey.apk) and patch again.",
                )

            fun addSpoofMetadata(name: String, value: String) {
                val element = document.createElement("meta-data")
                element.setAttribute("android:name", name)
                element.setAttribute("android:value", value)
                application.appendChild(element)
            }

            addSpoofMetadata("$GMS_CORE_PACKAGE.SPOOFED_PACKAGE_NAME", SWIFTKEY_PACKAGE)
            addSpoofMetadata("$GMS_CORE_PACKAGE.SPOOFED_PACKAGE_SIGNATURE", signature.trim())
        }
    }
}
