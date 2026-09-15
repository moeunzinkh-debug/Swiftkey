# ⌨️ SwiftKey — Text editing & Offline voice

បណ្តុំ patches សម្រាប់ **Microsoft SwiftKey Keyboard** ប្រើជាមួយ [Morphe Manager](https://morphe.software)។
គម្រោងនេះទុកតែ **Text editing toolbar** និង **Offline microphone** ប៉ុណ្ណោះ។

> **ការកែប្រែក្នុង source មិនទាន់ចេញ release ថ្មីទេ។** Bundle `v1.1.1` ដែលបានចេញផ្សាយមុន
> មិនមានការកែប្រែទាំងនេះទេ។ ត្រូវ build ពី source នេះ ឬរង់ចាំ release ថ្មី។ ការភ្ជាប់ toolbar និង
> native voice ថ្មីនៅត្រូវការតេស្តលើ APK/ឧបករណ៍ពិត ដូច្នេះកំណែគោលដៅទាំងពីរដាក់ជា **experimental**។

> កូដដើមបន្តពី [arandomhooman/hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches)
> និងស្នាដៃ ReVanced/Morphe ក្រោម **GPLv3**។

## 🩹 បញ្ជី patches ក្នុង source បច្ចុប្បន្ន

<!-- PATCHES_START EXPANDED -->
> **[v1.2.0-dev.1](https://github.com/moeunzinkh-debug/Swiftkey/releases/tag/v1.2.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Microsoft SwiftKey&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.13.14.5 | 🧪&nbsp;9.13.13.5 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Offline microphone](#offline-microphone) | Embeds a whisper.cpp offline speech engine and adds an Offline mic panel inside the keyboard. Choose Khmer, English, Thai or Chinese; tap Download model once (142 MiB shared multilingual model), then dictate without internet. No Google app or external speech provider required. Also redirects SwiftKey's Android SpeechRecognizer path; Microsoft Azure voice typing is not modified. |  |
| [Text editing toolbar](#text-editing-toolbar) | Adds a collapsible text editing toolbar inside the keyboard: cursor arrows, Home/End, selection, Select all, Cut, Copy, Paste, Undo and Redo. Actions use the current editor; Undo/Redo depend on that app's support. Does not change login, telemetry or Google services. |  |

</details>

<!-- PATCHES_END -->

តម្រូវការ​ឧបករណ៍៖ **Android 8.0 (API 26) ឬថ្មីជាងនេះ**។

គោលដៅ៖ `com.touchtype.swiftkey` កំណែ `9.13.13.5` និង `9.13.14.5`។
កូដ login, telemetry, GmsCore/MicroG, account permissions និង clone spoofing ចាស់ៗត្រូវបានដកចេញ។
មុខងារថ្មីទាំងពីរអាចជ្រើសដាច់ដោយឡែកបាន; ជ្រើស Text editing មិនបើក microphone ដោយស្វ័យប្រវត្តិទេ។

## ✏️ Text editing ក្នុង toolbar

បើក text field រួចចុច **Text editing** នៅលើ toolbar ថ្មីខាងលើក្តារចុច៖

- **← ↑ ↓ →**, **Home / End** សម្រាប់រំកិល cursor។
- **Select** បើក/បិទការជ្រើសអត្ថបទដោយប្រើប៊ូតុងរំកិល។
- **Select all**, **Cut**, **Copy**, **Paste**, **Undo**, **Redo**។

វាប្រើ `InputConnection` របស់ text field បច្ចុប្បន្ន មិនមែនកែអត្ថបទនៅក្នុង app settings ទេ។
Undo/Redo និង navigation ពឹងការគាំទ្ររបស់ app ដែលកំពុងវាយអក្សរ។ មិនរក្សាប្រវត្តិអត្ថបទ ឬ clipboard ផ្ទាល់ខ្លួនទេ។
Cut/Copy ត្រូវបិទក្នុង password fields។ Toolbar ជា row បន្ថែមក្នុង IME input view; មិនជំនួស UI ក្តារចុចដើមទេ។

## 🎙️ Offline microphone — ទាញយក model នៅពេលចង់ប្រើ

### របៀបប្រើពីក្នុង SwiftKey

1. ជ្រើស patch **Offline microphone** ពេល patch APK។
2. បើក text field ហើយចុច **Offline mic** លើ toolbar ក្នុង keyboard។
3. ជ្រើសភាសាមួយក្នុងចំណោម **ខ្មែរ (`km`), English (`en`), ไทย (`th`), 中文 (`zh`)**។
4. ចុច **Download model**។ មាន progress និង Cancel; ត្រូវមានអ៊ីនធឺណិតសម្រាប់ការទាញយកនេះ។
5. ពេលបង្ហាញ **Model ready** ចុច **Start**។ អនុញ្ញាត microphone ពេលប្រព័ន្ធសួរ រួចបើក Offline mic ហើយចុច Start ម្តងទៀត។
6. និយាយ រួចចុច **Stop**។ កូដបម្លែងសំឡេងនៅលើឧបករណ៍ ហើយបញ្ចូលអត្ថបទទៅ text field។
   មួយលើកកំណត់អតិបរមា **30 វិនាទី**។ ចុច Cancel ដើម្បីបោះបង់ដោយមិនបញ្ចូលអត្ថបទ។

### Library និង model

- **Engine:** [whisper.cpp v1.9.4](https://github.com/ggml-org/whisper.cpp/releases/tag/v1.9.4),
  commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`។ Native CPU library ត្រូវបាន build និងបញ្ចូលទៅក្នុង APK តាម patch។
- **Model:** multilingual `ggml-base.bin`, **147,951,465 bytes (ប្រហែល 142 MiB)** ពី
  [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp)។ មិនមែន `base.en` ដែលមានតែអង់គ្លេសទេ។
- Model មួយនេះប្រើរួមសម្រាប់ជម្រើសភាសាទាំង 4; មិនទាញ model ដដែល 4 ដងទេ។ UI កំណត់ត្រឹម 4 ភាសា
  ទោះបី model ពហុភាសាមានសមត្ថភាពភាសាផ្សេងទៀតក៏ដោយ។ មិនប្ដូរភាសាដែលមិនគាំទ្រទៅអង់គ្លេសដោយស្ងាត់ទេ។
- មិនវេច model weights ទៅក្នុង `.mpp` ឬ Git ហើយមិនទាញយកពេលបើក keyboard/ជ្រើសភាសាដោយស្វ័យប្រវត្តិទេ។
- ទាញតាម HTTPS ពី revision ដែលបាន pin និងផ្ទៀងផ្ទាត់ **SHA-256** មុន atomic install។
  ការទាញយកខូច/ផ្អាកមិនត្រូវបានចាត់ទុកជា model រួចរាល់ទេ។
- រក្សាទុកក្នុង private `noBackupFilesDir/offline-voice/` របស់ SwiftKey។ បន្ទាប់ពីទាញរួច អាចបិទអ៊ីនធឺណិតបាន។
  ប្ដូរភាសាមិនត្រូវការទាញ model ថ្មីទេ។ លុបទិន្នន័យ app ឬ uninstall នឹងលុប model ផងដែរ។

កូដ `VoskModelManager` ចាស់ដែលមានតែ downloader និងតំណ Khmer model ដែលគ្មាន asset ពិត ត្រូវបានដកចេញ។
ប្រើ Whisper.cpp ជំនួស ដើម្បីមាន engine ពហុភាសាសម្រាប់ជម្រើសទាំង 4 ដោយមិនបង្កើត URL model ក្លែងក្លាយ។
សមត្ថភាពភាសាមិនមែនជាការធានាគុណភាពទេ៖ ត្រូវសាក accuracy ជាពិសេសខ្មែរ/ថៃ និងល្បឿន/RAM លើទូរស័ព្ទពិត។

### Privacy និង microphone ដើម

- មិនផ្ញើ audio ទៅ cloud មិនរក្សាឯកសារសំឡេង និងមិន log អត្ថបទដែលសម្គាល់បានទេ។
- Microphone បើកតែពេលចុច Start/ប៊ូតុង mic។ ការទាញ model មិនបើក microphone ទេ។
- ការបិទ keyboard/ប្ដូរ text field បញ្ឈប់ការថត និងបោះបង់លទ្ធផលដែលមកយឺត ដើម្បីកុំបញ្ចូលទៅ field ខុស។
- Voice input ត្រូវបិទក្នុង password fields។
- ប៊ូតុង mic ដើមដែលហៅ Android `SpeechRecognizer` ត្រូវបានបង្វែរទៅ service ក្នុង APK ដូចគ្នា។
  **Microsoft Azure / Multi-modal voice typing មិនត្រូវបានកែទេ**; ប្រើ **Offline mic** toolbar
  ឬបិទ Multi-modal voice typing បើចង់ប្រើផ្លូវ Android speech ដើម។
- មិនត្រូវការ Google app, GmsCore, Kõnele ឬកម្មវិធី speech provider ខាងក្រៅទេ។

## 🛠️ Build និងតេស្ត

តម្រូវការ៖ **JDK 21**, Android SDK 36, **NDK 28.2.13676358**, **CMake 3.22.1**, Git។
GitHub Actions ដំឡើង native build tools ហើយប្រើ `GITHUB_TOKEN` សម្រាប់ Morphe registry។
Build ដំបូងទាញ source របស់ whisper.cpp ដែល pin តាម commit; មិនទាញ speech model ទេ។

```bash
# តេស្ត logic ដោយមិនទាញ model និងមិនត្រូវការ Android SDK
bash .github/scripts/test-features.sh

# Build .mpp + catalogue
./gradlew :patches:buildAndroid generatePatchesList --no-daemon

# ពិនិត្យថា .mpp មាន DEX, extension និង native libraries គ្រប់ ABI
find patches/build/libs -name 'patches*.mpp' ! -name '*-sources.mpp' ! -name '*-javadoc.mpp' \
  -exec python3 .github/scripts/verify-feature-bundle.py {} +
```

Morphe extension `.mpe` ផ្ទុក DEX តែប៉ុណ្ណោះ។ `exportVoiceNativeLibraries` វេច
`libswiftkey_whisper.so` ចូល `.mpp` ជា resource ហើយ microphone resource patch ចម្លងវាទៅ
`lib/<ABI>/` ក្នុង APK គោល។ វារក្សាតែ ABI ដែល APK ដើមមាន ដើម្បីកុំឲ្យ SwiftKey បាត់ native engine របស់ខ្លួន។
បើ APK ជា split សូម merge រួមទាំង native ABI split មុន patch។

មើល [បញ្ជីតេស្តលើឧបករណ៍](docs/TESTING.md) មុនចេញ release។

## 📂 រចនាសម្ព័ន្ធ

```text
patches/src/main/kotlin/hooman/morphe/patches/swiftkey/
├── editing/       # Text editing toolbar
├── voice/         # Offline microphone + native/manifest resources
├── toolbar/       # Shared IME lifecycle and input-view hooks (internal)
└── support/       # Bytecode invoke redirection helper
extensions/swiftkey/src/main/
├── cpp/           # Pinned whisper.cpp build + JNI bridge
└── java/app/morphe/extension/swiftkey/
    ├── toolbar/   # Text editing UI + language/download/recording panel
    └── voice/     # On-demand model manager, permission dialog, offline RecognitionService
```

## 📜 អាជ្ញាប័ណ្ណ

- កូដ patches និង extension ផ្ទាល់របស់គម្រោង៖ **GPLv3**; រក្សា attribution របស់គម្រោងដើម។
- whisper.cpp / ggml និង Whisper model៖ **MIT**។ Notices វេចក្នុង `.mpp` និង APK ដែលបាន patch។
- ឈ្មោះ Morphe ប្រើសម្រាប់ពិពណ៌នាភាព compatible ប៉ុណ្ណោះ; មើល `NOTICE`។
- Repo មិនចែកចាយ APK របស់ Microsoft ឬ model weights ទេ។ អ្នកប្រើត្រូវត្រៀម APK ផ្ទាល់ខ្លួន។
