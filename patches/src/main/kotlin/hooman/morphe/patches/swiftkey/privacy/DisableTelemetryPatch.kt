package hooman.morphe.patches.swiftkey.privacy

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility
import org.w3c.dom.Element

// Keep the manifest changes internal so users get the full telemetry shutdown as one patch. FCM and
// SwiftKeyJobService remain enabled because SwiftKey uses them for notifications and non-telemetry jobs.
private val disableTelemetryResourcesPatch = resourcePatch(
    description = "Disables SwiftKey crash, session, attribution, and legacy analytics collection. " +
        "Applied automatically with Disable telemetry.",
) {
    compatibleWith(swiftKeyCompatibility)

    execute {
        fun Element.androidName(): String = getAttribute("android:name")

        try {
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application")
                    .let { nodes ->
                        if (nodes.length == 1) nodes.item(0) as? Element else null
                    } ?: return@use

                fun setMetaData(name: String, value: String) {
                    val matches = document.getElementsByTagName("meta-data")
                        .let { nodes ->
                            (0 until nodes.length)
                                .mapNotNull { nodes.item(it) as? Element }
                                .filter { it.androidName() == name }
                        }
                    val element = matches.singleOrNull() ?: document.createElement("meta-data").also {
                        it.setAttribute("android:name", name)
                        application.appendChild(it)
                    }
                    element.setAttribute("android:value", value)
                }

                fun disableComponent(tag: String, name: String) {
                    val matches = document.getElementsByTagName(tag)
                        .let { nodes ->
                            (0 until nodes.length)
                                .mapNotNull { nodes.item(it) as? Element }
                                .filter { it.androidName() == name }
                        }
                    val element = matches.singleOrNull() ?: return
                    element.setAttribute("android:enabled", "false")
                }

                setMetaData("firebase_crashlytics_collection_enabled", "false")
                setMetaData("firebase_sessions_enabled", "false")

                disableComponent("receiver", "com.google.android.gms.analytics.AnalyticsReceiver")
                disableComponent("service", "com.google.android.gms.analytics.AnalyticsService")
                disableComponent("service", "com.google.android.gms.analytics.AnalyticsJobService")
                disableComponent("provider", "com.adjust.sdk.SystemLifecycleContentProvider")
            }
        } catch (e: Exception) {
            println("[DisableTelemetry] Warning: Manifest protection caught exception: ${e.message}")
        }

        try {
            document("res/values/bools.xml").use { document ->
                val matches = document.getElementsByTagName("bool")
                    .let { nodes ->
                        (0 until nodes.length)
                            .mapNotNull { nodes.item(it) as? Element }
                            .filter { it.getAttribute("name") == "exceptions_report_enabled" }
                    }
                matches.singleOrNull()?.textContent = "false"
            }
        } catch (e: Exception) {
            println("[DisableTelemetry] Warning: bools.xml protection caught exception: ${e.message}")
        }
    }
}

@Suppress("unused")
val disableTelemetryPatch = bytecodePatch(
    name = "Disable telemetry",
    description = "Stops SwiftKey's first-party telemetry records and uploads, Adjust attribution, " +
        "Crashlytics, Firebase Sessions, legacy Google Analytics, and app exception reporting. Push " +
        "messaging and the multipurpose job service stay enabled.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(disableTelemetryResourcesPatch)

    execute {
        // Drop records before the telemetry sinks can persist or upload them.
        TelemetryRecordDispatchFingerprint.methodOrNull?.let { dispatchMethod ->
            try {
                dispatchMethod.addInstructions(0, "return-void")
            } catch (e: Exception) {
                println("[DisableTelemetry] Warning: Failed to hook telemetry dispatch: ${e.message}")
            }
        }

        // Both suspend upload methods return kotlin.Unit.
        PrimaryTelemetryUploadFingerprint.methodOrNull?.let { primaryUploadMethod ->
            try {
                primaryUploadMethod.addInstructions(
                    0,
                    """
                        sget-object p1, Lql0/h0;->a:Lql0/h0;
                        return-object p1
                    """.trimIndent(),
                )
            } catch (e: Exception) {
                println("[DisableTelemetry] Warning: Failed to hook primary upload: ${e.message}")
            }
        }

        SecondaryTelemetryUploadFingerprint.methodOrNull?.let { secondaryUploadMethod ->
            try {
                secondaryUploadMethod.addInstructions(
                    0,
                    """
                        sget-object p2, Lql0/h0;->a:Lql0/h0;
                        return-object p2
                    """.trimIndent(),
                )
            } catch (e: Exception) {
                println("[DisableTelemetry] Warning: Failed to hook secondary upload: ${e.message}")
            }
        }

        // Adjust initialization
        AdjustInitializationFingerprint.methodOrNull?.let { adjustMethod ->
            try {
                val adjustCalls = adjustMethod.instructions.withIndex().filter { (_, instruction) ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.toString() ==
                        "Lcom/adjust/sdk/Adjust;->initSdk(Lcom/adjust/sdk/AdjustConfig;)V"
                }.toList()
                if (adjustCalls.size == 1) {
                    adjustMethod.replaceInstruction(adjustCalls.single().index, "nop")
                }
            } catch (e: Exception) {
                println("[DisableTelemetry] Warning: Failed to neutralize Adjust init: ${e.message}")
            }
        }
    }
}
