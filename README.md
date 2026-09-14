# ⌨️ SwiftKey Morphe Patches

បណ្តុំ​បំណះ (patches) ផ្ទាល់ខ្លួន​សម្រាប់​ **Microsoft SwiftKey Keyboard**
ដើម្បី​ប្រើ​ជាមួយ​កម្មវិធី [Morphe](https://morphe.software) (Morphe Manager) នៅលើ Android។

> 🙏 បំណះ​ដើម​រៀបចំ​ដោយ [arandomhooman/hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches)
> ដែល​បន្ត​ពី​ស្នាដៃ [ReVanced](https://github.com/ReVanced)។ គម្រោង​នេះ​យក​តែ​ផ្នែក SwiftKey មក​ចាប់ផ្តើម
> ហើយ​ចែកចាយ​ក្រោម​អាជ្ញាប័ណ្ណ​ដដែល​គឺ **GPLv3**។

## 🩹 បញ្ជី​បំណះ

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/moeunzinkh-debug/Swiftkey/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;8 patches total
<details open>
<summary>📦 Microsoft SwiftKey&nbsp;&nbsp;•&nbsp;&nbsp;8 patches</summary>
<br>

**🎯 Supported versions:**

| 9.13.13.5 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable cloud sign-in prompt](#disable-cloud-sign-in-prompt) | Keeps the optional Microsoft cloud sign-in onboarding from replacing SwiftKey's launcher, so local keyboard setup and settings remain usable without an account. This does not sign in or unlock cloud sync and other account-backed features. |  |
| [Disable telemetry](#disable-telemetry) | Stops SwiftKey's first-party telemetry records and uploads, Adjust attribution, Crashlytics, Firebase Sessions, legacy Google Analytics, and app exception reporting. Push messaging and the multipurpose job service stay enabled. |  |
| [Fix microphone voice input](#fix-microphone-voice-input) | Makes the microphone button work like on Gboard without installing Google: SwiftKey's speech checks stop hard-requiring the Google app and any Android RecognitionService (the system default, e.g. Kõnele/Vosk offline or Speech Recognition & Synthesis) is used. Install and set a speech recognition provider in system settings first; the patch only removes the Google gate, it does not ship a speech engine. |  |
| [GmsCore Bytecode Redirect (MicroG RE 7.1.2)](#gmscore-bytecode-redirect-microg-re-7-1-2) | Rewires the app's Google Play Services references (GMS package name, account vendor, c2dm/GMS permissions and provider authorities) to MicroG RE's GmsCore package app.revanced.android.gms. Service intent actions are kept literal because GmsCore serves them under the original names. Enable together with the other GmsCore patches. |  |
| [GmsCore Signature and Availability Bypass](#gmscore-signature-and-availability-bypass) | Stops the bundled Google Play Services availability/version checks from blocking the app when Google Play Services is absent and GmsCore (MicroG RE) takes its place: the enforce check returns immediately and the availability result reads SUCCESS (0). Checks that this build does not include are skipped instead of failing. |  |
| [GmsCore support (MicroG RE)](#gmscore-support-microg-re) | Umbrella patch for devices running MicroG RE (app.revanced.android.gms) instead of Google Play Services. Pulls in: GmsCore bytecode redirect, GmsCore signature and availability bypass, MicroG account permissions and clone process-name spoofing, plus the manifest queries/permissions they need. Requires MicroG RE 7.x installed; it does not provide Microsoft account/cloud features. | • Original signing certificate SHA-1 (cloned builds only) |
| [MicroG Account Permissions](#microg-account-permissions) | Requests the GmsCore account permissions (GET_ACCOUNTS and app.revanced.gms.EXTENDED_ACCESS) at runtime the first time a SwiftKey activity starts, so account access works against MicroG RE without an ADB helper. Does nothing when no GmsCore is installed. |  |
| [Process Name Spoofing for Clone Support](#process-name-spoofing-for-clone-support) | For cloned builds (package suffix such as .morphe), strips the clone suffix from the process name the app reads through Application.getProcessName()/ActivityThread, so process-name based routing behaves exactly like the original install. No-op on normal, uncloned builds. |  |

</details>

<!-- PATCHES_END -->

គោលដៅ​បច្ចុប្បន្ន៖ **Microsoft SwiftKey** `com.touchtype.swiftkey` កំណែ **9.13.13.5**

| បំណះ | សង្ខេប​មុខងារ |
|------|--------------|
| **Disable cloud sign-in prompt** | បិទ​អេក្រង់​បង្ខំ​ឲ្យ​ចូល​គណនី Microsoft cloud ពេល​បើក​កម្មវិធី ដើម្បី​ឲ្យ​ការដំឡើង​ក្តារចុច និង​ការកំណត់​មូលដ្ឋាន​ប្រើ​បាន​ដោយ​មិនចាំបាច់​ login (មិនមែន​ចូលគណនី ហើយ​មិន​ដោះ cloud sync) |
| **Disable telemetry** | បញ្ឈប់​ការបញ្ជូន​ទិន្នន័យតាមដាន៖ telemetry ផ្ទាល់របស់ SwiftKey, Adjust, Crashlytics, Firebase Sessions, Google Analytics ចាស់ និង​របាយការណ៍​គាំង — ខណៈ​ push notifications និង job service នៅ​ដំណើរការ |
| **🎙️ Fix microphone voice input** | ដោះ​ទ្វារ​បង្ខំ "ទាញយក Google Voice Search"៖ ប៊ូតុង​មីក្រូហ្វូន​ហៅ​ម៉ាស៊ីន​សម្គាល់​សំឡេង​លំនាំដើម​របស់​ប្រព័ន្ធ (Kõnele/Vosk ក្រៅ​បណ្ដាញ ឬ Speech Recognition & Synthesis) បាន​ភ្លាមៗ​ដូច Gboard |
| **GmsCore support (MicroG RE)** | បំណះ​ឆ័ត្រ​សម្រាប់​ឧបករណ៍​គ្មាន GMS ដែល​ដំឡើង MicroG RE — ទាញ​យក​បំណះ​រង​ទាំង ៤​ខាងក្រោម |
| ↳ GmsCore Bytecode Redirect (MicroG RE 7.1.2) | ប្ដូរ​ទិស​រាល់​សេចក្តីយោង Google Play Services (កញ្ចប់ GMS, vendor គណនី, សិទ្ធិ c2dm/GMS, authorities) ទៅ `app.revanced.android.gms` |
| ↳ GmsCore Signature and Availability Bypass | បិទ​ការ​ត្រួតពិនិត្យ​ភាព​មាន និង​លេខ​កំណែ GMS ដែល​រារាំង​កម្មវិធី​ពេល​គ្មាន Play Services (តម្លៃ​ត្រឡប់ = SUCCESS) |
| ↳ MicroG Account Permissions | ស្នើ​សិទ្ធិ `GET_ACCOUNTS` និង `app.revanced.gms.EXTENDED_ACCESS` នៅ​ពេល​រត់ ឲ្យ​ការ​ចូល​គណនី​លើ​ GmsCore ដំណើរការ |
| ↳ Process Name Spoofing for Clone Support | ពេល​ដាក់​ជា​ក្លូន (មាន​បច្ច័យ `.morphe`) កាត់​បច្ច័យ​នេះ​ចេញ​ពី​ឈ្មោះ​ដំណើរការ​ដែល​កម្មវិធី​អាន ដើម្បី​កុំឲ្យ routing ខាងក្នុង​វង្វេង/គាំង |

### 🎙️ តម្រូវការ​សម្រាប់​មីក្រូហ្វូន

បំណះ​មីក្រូហ្វូន​ដក​តែ​ច្រក​របាំង Google ចេញ — វា **មិនមាន​ម៉ាស៊ីន​សម្គាល់​សំឡេង​ភ្ជាប់​មក​ទេ**។ អ្នកត្រូវ៖

1. ដំឡើង​កម្មវិធី RecognitionService ណាមួយ៖
   - **[Kõnele](https://github.com/Kaljurand/K6nele)** — ប្រើ Vosk សម្គាល់​ក្រៅ​បណ្ដាញ​ទាំងស្រុង (ណែនាំ)
   - ឬ **Speech Recognition & Synthesis** (`com.google.android.tts`)
2. កំណត់​វា​ជា​ម៉ាស៊ីន​លំនាំដើម៖ *Settings → System → Languages & input → Voice input / Speech recognition*។
3. បិទ **Multi-modal voice typing** ក្នុង SwiftKey Settings → Rich input បើ​ចង់​ឲ្យ​ប្រើ​ម៉ាស៊ីន​ប្រព័ន្ធ (មិនមែន​សេវា Azure របស់ Microsoft)។

### 🧩 តម្រូវការ​សម្រាប់ GmsCore

- ដំឡើង **[MicroG RE](https://github.com/MorpheApp/MicroG-RE)** (កញ្ចប់ `app.revanced.android.gms`) ហើយ​បន្ថែម​គណនី​ក្នុង​កម្មវិធី microG (មិនមែន​ក្នុង SwiftKey)។
- បំណះ GmsCore មិន​ដោះ Microsoft cloud login/sync ទេ — វា​គ្រាន់តែ​ធ្វើ​ឲ្យ​ផ្នែក​ដែល​ពឹង Firebase/GMS (push ជាដើម) ដើរ​តាម GmsCore។
- **សម្រាប់​ការ​ក្លូន​ប៉ុណ្ណោះ**៖ បើ​អ្នក​ប្ដូរ​ឈ្មោះ​កញ្ចប់ (clone) សូម​បំពេញ option *Original signing certificate SHA-1* ដែល​អាន​ពី APK ដើម​ដោយ​ពាក្យ `apksigner verify --print-cert SwiftKey.apk`។

## 📥 របៀប​ដំឡើង និង​ប្រើ

1. ត្រៀម **APK របស់ SwiftKey កំណែ 9.13.13.5** ឲ្យ​ចំ​កំណែ (ទាញពី APKMirror/APKCombo ឬ​នាំចេញ​ពី​ឧបករណ៍)។
   Bundle ប្រភេទ `.apks`/`.xapk` អាច​ប្រើ​បាន — Morphe Manager បញ្ចូល​គ្នា​ឲ្យ​ស្វ័យប្រវត្តិ។
2. បើក **Morphe Manager** → Add patch source → ដាក់ URL repo នេះ។
3. ជ្រើស SwiftKey ជ្រើស​បំណះ​ដែល​ចង់​បាន បន្ទាប់មក Patch និង​ដំឡើង។

> ⚠️ ការ patch ធ្វើ​ឲ្យ​គេ​ចុះហត្ថលេខា APK ថ្មី៖ ការចូល​គណនី Google អាច​ឈប់ដំណើរការ — ប្រើ​ការចូល​តាម
> email វិញ​បើ​ចាំបាច់។ មុខងារ​ដែល​គណនា​នៅ​លើ​ server (ឧ. cloud sync) មិនត្រូវ​បាន​ដោះសោ​ទេ
> ព្រោះ​បំណះ​ធ្វើ​បាន​តែ​ក្នុង​ម៉ាស៊ីន (client-side) ប៉ុណ្ណោះ។

## 🛠️ ការ build ក្នុង​ម៉ាស៊ីន​មូលដ្ឋាន

តម្រូវការ៖ **JDK 21** (GitHub Actions ប្រើ Temurin 21)។

```bash
# build ឲ្យ​ឃើញ .mpp bundle (ចេញនៅ patches/build/libs/)
./gradlew :patches:buildAndroid

# បង្កើត patches-list.json (ប្រើ​ក្នុង​ដំណើរការ​ចេញផ្សាយ)
./gradlew generatePatchesList
```

> ឧបករណ៍ Morphe plugin ទាញ​ពី GitHub Packages (`MorpheApp/registry`)។
> ក្នុង GitHub Actions វា​ប្រើ `GITHUB_TOKEN` ស្វ័យប្រវត្តិ; ក្នុង​ម៉ាស៊ីន​មូលដ្ឋាន​អ្នក​អាច
> ដាក់ `gpr.user`/`gpr.key` ក្នុង `~/.gradle/gradle.properties` បើ​ទាញយក​មិនបាន
> (public package ជាទូទៅ​បើក​ចំហ)។

## 📂 រចនាសម្ព័ន្ធ​គម្រោង

```
.
├── settings.gradle.kts          # កំណត់ repo plugin របស់ Morphe + extension config
├── build.gradle.kts             # root project
├── gradle.properties            # លេខកំណែ + group (ប្តូរ​តាម​ខ្លួនឯង)
├── patches/
│   ├── build.gradle.kts         # ឈ្មោះ/អ្នកនិពន្ធ bundle (about { ... })
│   └── src/main/kotlin/hooman/morphe/patches/swiftkey/
│       ├── SwiftKeyCompatibility.kt   # ថត​គោលដៅ៖ package, កំណែ, ពណ៌​រូបតំណាង
│       ├── login/                     # បំណះ "Disable cloud sign-in prompt"
│       │   ├── DisableCloudSignInPromptPatch.kt
│       │   └── Fingerprints.kt
│       ├── privacy/                   # បំណះ "Disable telemetry"
│       │   ├── DisableTelemetryPatch.kt
│       │   └── Fingerprints.kt
│       ├── voice/                     # 🎙️ បំណះ "Fix microphone voice input"
│       │   └── MicrophoneFixPatch.kt
│       ├── microg/                    # ក្រុម​បំណះ GmsCore/MicroG RE
│       │   ├── GmsCoreSupportPatch.kt            # បំណះ​ឆ័ត្រ
│       │   ├── GmsCoreRedirectPatch.kt + GmsConstants.kt
│       │   ├── AvailabilityBypassPatch.kt + Fingerprints.kt
│       │   ├── AccountPermissionsPatch.kt
│       │   └── ProcessNameSpoofPatch.kt
│       └── support/                   # កូដ​ចែករំលែក៖ ការកែ manifest + ឧបករណ៍​ឆ្លាស់ invoke
├── extensions/
│   ├── proguard-rules.pro
│   └── swiftkey/                      # Extension .mpe (Java runtime ចាក់​បញ្ចូល APK)
│       └── src/main/java/app/morphe/extension/swiftkey/
│           ├── MicSupport.java        # មីក្រូហ្វូន + ការ​ជ្រើស​ម៉ាស៊ីន​សម្គាល់​សំឡេង
│           └── GmsSupport.java        # សិទ្ធិ​គណនី microG + ការ​ធ្វើ​ឲ្យ​ឈ្មោះ​ដំណើរការ​ស្អាត
└── .github/                     # CI build + semantic-release + backmerge
```

## ➕ របៀប​បន្ថែម​មុខងារ​ថ្មី (បំណះ​ថ្មី)

1. **បង្កើត​ថត​ប្រភេទ​ថ្មី** ក្រោម `swiftkey/` (ឧ. `swiftkey/theme/`)។
2. **ស្វែងរក​ចំណុចគោលដៅ​ក្នុង APK** ជាមួយ​ឧបករណ៍ APK decompile
   (ឧ. jadx / apktool) ហើយ​កត់សម្គាល់​លក្ខណៈ​សម្គាល់​ដែល​មិន​សូវ​ប្រែ​តាម​កំណែ៖
   អក្សរ (strings) ដែល​កូដ​ប្រើ, ការហៅ​មេតូដ, opcode, រាង​ប៉ារ៉ាម៉ែត្រ។
3. **បង្កើត `Fingerprints.kt`** — សរសេរ `Fingerprint(...)` សម្រាប់​ "ខ្ទាស់" មេតូដ​គោលដៅ​
   (R8 obfuscation ប្តូរ​ឈ្មោះ `a/b/c` រាល់កំណែ ដូច្នេះ​កុំ​ពឹង​ឈ្មោះ​ថ្នាក់)។
4. **បង្កើត​ឯកសារ​បំណះ** ឧ. `EnableDarkThemePatch.kt`៖
   - `bytecodePatch { name = ...; description = ...; compatibleWith(swiftKeyCompatibility); execute { ... } }`
     សម្រាប់​កែ smali (ឧ. `method.addInstructions(0, "return-void")`)
   - ឬ `resourcePatch { ... }` សម្រាប់​កែ `AndroidManifest.xml` / `res/values/*.xml`
   - ប៊ូតុង/ជម្រើស​ដែល​អ្នកប្រើ​ប្ដូរ​បាន៖ មើល API `options {}` របស់ Morphe
5. **ប្រើ​ `PatchException`** ពេល​រក fingerprint មិនឃើញ — វា​ប្រាប់​អ្នក​ថា​កំណែ​ថ្មី​កូដ​ប្រែ​ហើយ
   ជាជាង​បង្កើត APK ខូច​ដោយ​ស្ងាត់ៗ។
6. **build សាកល្បង** ជាមួយ `./gradlew :patches:buildAndroid` រួច​សាក​ក្នុង Morphe Manager។
7. បើ​មុខងារ​ត្រូវការ **កូដ​ថ្មី​ទាំងមូល** (ដូច fetch បណ្ដាញ, UI ផ្ទាល់ខ្លួន) បង្កើត​ជា
   extension `.mpe` ក្រោម `extensions/swiftkey/` ហើយ​ហៅ​វា​តាម `extendWith("extensions/swiftkey.mpe")`
   ក្នុង​បំណះ (យក​គំរូ​តាម​បំណះ Twitch emotes របស់​គម្រោង​ដើម)។

ទម្រង់សារ commit សម្រាប់​ឲ្យ​លេខកំណែ​ដំឡើង​ដោយ​ស្វ័យប្រវត្តិ៖

| សារ | ឥទ្ធិពល |
|-----|---------|
| `feat: ...` | minor version (មុខងារថ្មី) |
| `fix: ...` / `perf: ...` / `bump: ...` | patch version |

## 📜 អាជ្ញាប័ណ្ណ និង​សម្គាល់​ស្នាដៃ

- កូដបំណះ៖ **GPLv3** (យក​ពី [hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches))។
- ឈ្មោះ **Morphe** ជា​កម្មសិទ្ធិ​របស់​គម្រោង Morphe — សូម​មើល `NOTICE` (forks ត្រូវ​ប្រើ​ឈ្មោះ​ផ្ទាល់ខ្លួន;
  ហៅ​ខ្លួនឯង​ថា "compatible with Morphe" បាន​តែ​ក្នុង​ន័យ​ពណ៌នា)។
- repo នេះ​មិន​ចែកចាយ APK របស់ Microsoft ទេ — អ្នកប្រើ​ត្រូវ​ត្រៀម APK ដោយ​ខ្លួនឯង។
