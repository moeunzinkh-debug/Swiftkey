package hooman.morphe.patches.simsimi.support

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import hooman.morphe.patches.simsimi.simsimiCompatibility
import org.w3c.dom.Element

const val GMS_CORE_PACKAGE = "app.revanced.android.gms"
const val GMS_VENDOR_GROUP_ID = "app.revanced"

const val PERMISSION_GET_ACCOUNTS = "android.permission.GET_ACCOUNTS"
const val PERMISSION_GMS_EXTENDED_ACCESS = "app.revanced.gms.EXTENDED_ACCESS"

const val SIMSIMI_PACKAGE = "com.ismaker.android.simsimi"

internal var spoofedSignatureProvider: () -> String? = { null }

internal val simsimiSupportManifestPatch = resourcePatch(
    description = "Prepares the AndroidManifest for GmsCore support (package visibility queries, " +
        "account permissions, c2dm rename, clone spoof metadata). Applied automatically with " +
        "GmsCore support.",
) {
    compatibleWith(simsimiCompatibility)

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
                ?: throw PatchException("SimSimi: AndroidManifest root element not found.")

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
        }
    }

    finalize {
        val currentPackage = packageMetadata.packageName
        if (currentPackage == SIMSIMI_PACKAGE) return@finalize

        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application")
                .let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }
                .singleOrNull()
                ?: throw PatchException("SimSimi: expected exactly one application element.")

            val signature = spoofedSignatureProvider()?.takeIf { it.isNotBlank() }
                ?: throw PatchException(
                    "SimSimi: this build is cloned/renamed ($currentPackage), so GmsCore needs the " +
                        "original app signing SHA-1 to spoof its identity. Set the \"Original signing " +
                        "certificate SHA-1\" option on the \"GmsCore support (MicroG RE)\" patch " +
                        "(read it from the unmodified APK with: apksigner verify --print-cert " +
                        "SimSimi.apk) and patch again.",
                )

            fun addSpoofMetadata(name: String, value: String) {
                val element = document.createElement("meta-data")
                element.setAttribute("android:name", name)
                element.setAttribute("android:value", value)
                application.appendChild(element)
            }

            addSpoofMetadata("$GMS_CORE_PACKAGE.SPOOFED_PACKAGE_NAME", SIMSIMI_PACKAGE)
            addSpoofMetadata("$GMS_CORE_PACKAGE.SPOOFED_PACKAGE_SIGNATURE", signature.trim())
        }
    }
}
