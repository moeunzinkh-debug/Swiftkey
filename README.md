# ⌨️ SwiftKey Morphe Patches

បណ្តុំ​បំណះ (patches) ផ្ទាល់ខ្លួន​សម្រាប់​ **Microsoft SwiftKey Keyboard**
ដើម្បី​ប្រើ​ជាមួយ​កម្មវិធី [Morphe](https://morphe.software) (Morphe Manager) នៅលើ Android។

> 🙏 បំណះ​ដើម​រៀបចំ​ដោយ [arandomhooman/hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches)
> ដែល​បន្ត​ពី​ស្នាដៃ [ReVanced](https://github.com/ReVanced)។ គម្រោង​នេះ​យក​តែ​ផ្នែក SwiftKey មក​ចាប់ផ្តើម
> ហើយ​ចែកចាយ​ក្រោម​អាជ្ញាប័ណ្ណ​ដដែល​គឺ **GPLv3**។

## 🩹 បញ្ជី​បំណះ

<!-- PATCHES_START EXPANDED -->
(តារាងនេះ​នឹង​ត្រូវ​បាន​បន្ទាន់​សម័យ​ដោយ​ស្វ័យប្រវត្តិ​ពេល​ចេញផ្សាយ — សូម​កុំ​លុប comment markers)
<!-- PATCHES_END -->

គោលដៅ​បច្ចុប្បន្ន៖ **Microsoft SwiftKey** `com.touchtype.swiftkey` កំណែ **9.13.13.5**

| បំណះ | សង្ខេប​មុខងារ |
|------|--------------|
| **Disable cloud sign-in prompt** | បិទ​អេក្រង់​បង្ខំ​ឲ្យ​ចូល​គណនី Microsoft cloud ពេល​បើក​កម្មវិធី ដើម្បី​ឲ្យ​ការដំឡើង​ក្តារចុច និង​ការកំណត់​មូលដ្ឋាន​ប្រើ​បាន​ដោយ​មិនចាំបាច់​ login (មិនមែន​ចូលគណនី ហើយ​មិន​ដោះ cloud sync) |
| **Disable telemetry** | បញ្ឈប់​ការបញ្ជូន​ទិន្នន័យតាមដាន៖ telemetry ផ្ទាល់របស់ SwiftKey, Adjust, Crashlytics, Firebase Sessions, Google Analytics ចាស់ និង​របាយការណ៍​គាំង — ខណៈ​ push notifications និង job service នៅ​ដំណើរការ |

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
│       └── privacy/                   # បំណះ "Disable telemetry"
│           ├── DisableTelemetryPatch.kt
│           └── Fingerprints.kt
├── extensions/                  # កូដ​ផ្ទាល់ (.mpe) សម្រាប់​ចាក់​បញ្ចូល APK — ទុកសម្រាប់​មុខងារថ្មី
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
