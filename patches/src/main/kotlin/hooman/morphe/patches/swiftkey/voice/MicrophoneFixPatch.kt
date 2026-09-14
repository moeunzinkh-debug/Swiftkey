package hooman.morphe.patches.swiftkey.voice

import app.morphe.patcher.patch.bytecodePatch
import hooman.morphe.patches.swiftkey.support.InvokeRedirect
import hooman.morphe.patches.swiftkey.support.redirectInvokes
import hooman.morphe.patches.swiftkey.support.swiftKeySupportManifestPatch
import hooman.morphe.patches.swiftkey.swiftKeyCompatibility

private const val EXTENSION = "Lapp/morphe/extension/swiftkey/MicSupport;"

private const val CONTEXT = "Landroid/content/Context;"
private const val COMPONENT_NAME = "Landroid/content/ComponentName;"
private const val PACKAGE_MANAGER = "Landroid/content/pm/PackageManager;"
private const val PACKAGE_INFO = "Landroid/content/pm/PackageInfo;"
private const val PACKAGE_INFO_FLAGS = "Landroid/content/pm/PackageInfoFlags;"
private const val SPEECH_RECOGNIZER = "Landroid/speech/SpeechRecognizer;"

/**
 * 🎙️ ជួសជុល​មីក្រូហ្វូន/Voice typing — ចុច​និយាយ​ភ្លាម​ប្រើ​បាន លែង​តម្រូវ​ឲ្យ​ទាញយក Google។
 *
 * មូលហេតុ​ដើម៖ SwiftKey​ប្រើ​ SpeechRecognizer របស់ Android (បទពិសោធន៍ "Google Voice Typing"
 * ចាស់)។ វា​ពិភាក្សា​តែ​ជាមួយ​ម៉ាស៊ីន Google (Google app / Speech Recognition &amp; Synthesis)
 * ហើយ​បើ​រក​មិនឃើញ​វា​បង្ហាញ​សារ "To use voice input, you must download and install
 * Google Voice Search"។ នៅ​លើ​ឧបករណ៍​គ្មាន GMS (microG/កម្រោម​de-Googled) គ្មាន​ម៉ាស៊ីន​ណា​ត្រូវ
 * បាន​ពិភាក្សា ទោះបី​អ្នក​បាន​ដំឡើង​ម៉ាស៊ីន​ឯករាជ្យ (Kõnele/Vosk) ក៏​ដោយ។
 *
 * បំណះ​នេះ​ប្ដូរ​ការ​ហៅ​ API ទាំង​បួន​ទៅ​កាន់​កូដ​ផ្ទាល់​ (extensions/swiftkey/MicSupport)៖
 *  1. SpeechRecognizer.isRecognitionAvailable(Context) → រកមើល​ម៉ាស៊ីន RecognitionService
 *     ណា​មួយ​ក្នុង​ប្រព័ន្ធ មិនមែន​ត្រឹមតែ Google
 *  2. SpeechRecognizer.createSpeechRecognizer(Context[, ComponentName]) → ទម្លាក់
 *     ComponentName របស់ Google ពេល​វា​មិនមាន ហើយ​ប្រើ​ម៉ាស៊ីន​លំនាំដើម​របស់​ប្រព័ន្ធ
 *  3. PackageManager.getPackageInfo (ទម្រង់ int និង PackageInfoFlags) → ពេល​ស្វែងរក
 *     កញ្ចប់ Google មិនឃើញ ឆ្លើយ​ជំនួស​ដោយ​កញ្ចប់​ម៉ាស៊ីន​សម្គាល់​សំឡេង​ជាក់ស្តែង
 *     (ទប់​ទ្វារ "ត្រូវតែ​ដំឡើង Google app")
 *
 * ដំណើរការ​លើ​ទាំង​កម្មវិធី​ធម្មតា និង​បន្ទាប់ពី​ប្ដូរ​ហត្ថលេខា។ លើ​ទូរស័ព្ទ​ដែល​មាន Google ពិត
 * ឥរិយាបថ​នៅ​ដដែល (ComponentName ដែល​មាន​ពិត​ត្រូវ​បាន​រក្សា)។
 *
 * តម្រូវការ​ចាំបាច់​របស់​អ្នកប្រើ ៖ ត្រូវ​មាន RecognitionService ណា​មួយ​នៅ​ក្នុង​ប្រព័ន្ធ
 * (Kõnele សម្រាប់ Vosk ក្រៅ​បណ្ដាញ​ទាំងស្រុង; ឬ Speech Recognition &amp; Synthesis) ហើយ
 * កំណត់​វា​ជា​ម៉ាស៊ីន​សម្គាល់​សំឡេង​លំនាំដើម​ក្នុង​ការកំណត់​ប្រព័ន្ធ។
 */
@Suppress("unused")
val fixMicrophoneVoiceInputPatch = bytecodePatch(
    name = "Fix microphone voice input",
    description = "Makes the microphone button work like on Gboard without installing Google: " +
        "SwiftKey's speech checks stop hard-requiring the Google app and any Android " +
        "RecognitionService (the system default, e.g. Kõnele/Vosk offline or Speech Recognition " +
        "& Synthesis) is used. Install and set a speech recognition provider in system settings " +
        "first; the patch only removes the Google gate, it does not ship a speech engine.",
) {
    compatibleWith(swiftKeyCompatibility)
    dependsOn(swiftKeySupportManifestPatch)
    extendWith("extensions/swiftkey.mpe")

    execute {
        val replaced = redirectInvokes(
            // 1) isRecognitionAvailable(Context)Z — បើក​ទ្វារ​សម្រាប់​ម៉ាស៊ីន​ឯករាជ្យ
            InvokeRedirect(
                definingClass = SPEECH_RECOGNIZER,
                name = "isRecognitionAvailable",
                parameters = listOf(CONTEXT),
                returnType = "Z",
                replacementDescriptor = EXTENSION,
                replacementName = "isRecognitionAvailable",
            ),
            // 2a) createSpeechRecognizer(Context)
            InvokeRedirect(
                definingClass = SPEECH_RECOGNIZER,
                name = "createSpeechRecognizer",
                parameters = listOf(CONTEXT),
                returnType = SPEECH_RECOGNIZER,
                replacementDescriptor = EXTENSION,
                replacementName = "createSpeechRecognizer",
            ),
            // 2b) createSpeechRecognizer(Context, ComponentName)
            InvokeRedirect(
                definingClass = SPEECH_RECOGNIZER,
                name = "createSpeechRecognizer",
                parameters = listOf(CONTEXT, COMPONENT_NAME),
                returnType = SPEECH_RECOGNIZER,
                replacementDescriptor = EXTENSION,
                replacementName = "createSpeechRecognizer",
            ),
            // 3a) PackageManager.getPackageInfo(String, int)
            InvokeRedirect(
                definingClass = PACKAGE_MANAGER,
                name = "getPackageInfo",
                parameters = listOf("Ljava/lang/String;", "I"),
                returnType = PACKAGE_INFO,
                replacementDescriptor = EXTENSION,
                replacementName = "getPackageInfo",
            ),
            // 3b) PackageManager.getPackageInfo(String, PackageInfoFlags) (API 33+)។
            // មេតូដ​ជំនួស​ប្រកាស​ប៉ារ៉ាម៉ែត្រ​ទីបីជា Object (មិន​ពឹង​ថ្នាក់ API 33 ពេល​ចងក្រង)
            // — PackageInfoFlags ជា​ប្រភេទ​រង​របស់ Object ទើប ART ទទួល​យក។
            InvokeRedirect(
                definingClass = PACKAGE_MANAGER,
                name = "getPackageInfo",
                parameters = listOf("Ljava/lang/String;", PACKAGE_INFO_FLAGS),
                returnType = PACKAGE_INFO,
                replacementDescriptor = EXTENSION,
                replacementName = "getPackageInfo",
                replacementParameters = listOf("Ljava/lang/String;", "Ljava/lang/Object;"),
            ),
        )

        check(replaced > 0) {
            "SwiftKey microphone: no SpeechRecognizer/PackageManager call sites were found. " +
                "This version routes voice input differently; the fingerprints need updating."
        }
    }
}
