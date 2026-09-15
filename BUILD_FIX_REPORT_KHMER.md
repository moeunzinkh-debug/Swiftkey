# 📋 របាយការណ៍ជួសជុល Build Log Error — ពន្យល់ជាភាសាខ្មែរ

## 🔍 សង្ខេបបញ្ហា (Summary)

GitHub Actions មានការបរាជ័យ ៣ ប្រភេទ៖

1. **Build .mpp bundle បរាជ័យ** — `run 34943884960`, `34944562285`, `34944847096`
   - Step: `Build .mpp bundle` → `Process completed with exit code 1`
2. **Release បរាជ័យ** — `run 34925147012`, `34875945565`
   - Step: `Build .mpp bundle` ជោគជ័យ ប៉ុន្តែ `Release` (semantic-release) បរាជ័យ
3. **Back-merge main → dev បរាជ័យ** — `run 34945439049` (បន្ទាប់ពីជួសជុល Build)
   - Step: `Back-merge main into dev` → conflict មិនមែន generated files

បន្ទាប់ពីជួសជុល Run ចុងក្រោយ `34946437108` **ជោគជ័យទាំងអស់** (Build, Verify, Release, Attest, Upload, Back-merge).

---

## 🐛 កំហុសទី 1: Build .mpp bundle — Compile Error

### មូលហេតុពិត

#### 1.1 ProGuard/R8 រក្សាតែ `swiftkey` extension

`extensions/proguard-rules.pro` ដើម៖

```proguard
-keep class app.morphe.extension.swiftkey.** { *; }
-repackageclasses 'app.morphe.extension.swiftkey'
```

- ពេលបន្ថែម `extensions/simsimi` ក្នុង PR #10, R8 នៅតែ obfuscate `simsimi` classes
- `-repackageclasses 'app.morphe.extension.swiftkey'` បង្ខំឲ្យ class ទាំងអស់ (រួមទាំង simsimi) ចូល package `swiftkey` → collision ឬ class descriptor `Lapp/morphe/extension/simsimi/GmsSupport;` បាត់
- Morphe plugin build extension បរាជ័យ ឬ .mpe ខូច → `:patches:buildAndroid` បរាជ័យ

#### 1.2 `extension { name = ... }` មិនមាន

`extensions/*/build.gradle.kts` មិនកំណត់ `extension { name = "extensions/xxx.mpe" }` ច្បាស់លាស់
- Plugin ប្រហែលប្រើ default name ខុស → `extendWith("extensions/simsimi.mpe")` រក file មិនឃើញ

#### 1.3 Kotlin Compile Error — `addInstructions` លើ immutable Method

`RemoveAdsPatch.kt` និង `UnlockMembershipPatch.kt`៖

```kotlin
getAllClassesWithStrings().forEach { classDef -> // classDef = immutable ClassDef
    classDef.methods.forEach { method -> // method = immutable Method
        method.addInstructions(0, "return-void") // ❌ addInstructions ត្រូវការ MutableMethod
    }
}
```

`InstructionExtensions.addInstructions` កំណត់សម្រាប់ `MutableMethod` តែប៉ុណ្ណោះ
- `getAllClassesWithStrings()` ត្រឡប់ `List<ClassDef>` immutable
- ហៅ `addInstructions` លើ immutable → Kotlin compiler error → gradle build បរាជ័យ

នេះជាហេតុផលចម្បងដែល `Build .mpp bundle` បរាជ័យនៅ PR #10 (0ca530c Remove ads patch)

#### 1.4 ប្រើ `p0` ជំនួស `v0` សម្រាប់ return value

```kotlin
// instance method: p0 = this, មិនគួរ overwrite
"const/4 p0, 0x0\nreturn p0" // ❌ ខុស — បំផ្លាញ this
```

គួរប្រើ `v0` ជានិច្ច ដើម្បីជៀសវាង ART verification error ពេល runtime

---

### ✅ ដំណោះស្រាយ

**1. `extensions/proguard-rules.pro` — រក្សាទាំងអស់ + dontobfuscate**

```proguard
-keep class app.morphe.extension.** { *; }
-dontobfuscate
-dontoptimize
-keepattributes *
-repackageclasses 'app.morphe.extension'
-keep class kotlin.jvm.internal.Intrinsics { public static *; }
```

- រក្សា `swiftkey` និង `simsimi` ទាំងពីរ
- `dontobfuscate` ជៀសវាង collision ជាមួយ app classes `a, b, c...`
- `repackageclasses 'app.morphe.extension'` — root package មិនប៉ះទង្គិចគ្នា

**2. `extensions/*/build.gradle.kts` — បន្ថែម extension name**

```kotlin
extension {
    name = "extensions/swiftkey.mpe" // ឬ simsimi.mpe
}
configure<ApplicationExtension> { ... }
```

**3. Fix `RemoveAdsPatch.kt` និង `UnlockMembershipPatch.kt`**

```kotlin
getAllClassesWithStrings().forEach { classDef ->
    val mutableClass = try { mutableClassDefBy(classDef) } catch (_: Exception) { return@forEach }
    classDef.methods.forEach { originalMethod ->
        val mutableMethod = mutableClass.methods.firstOrNull { 
            it.name == originalMethod.name && it.returnType == originalMethod.returnType && ...
        } ?: return@forEach
        mutableMethod.addInstructions(0, "const/4 v0, 0x0\nreturn v0") // ✅ ប្រើ v0
    }
}
```

- យក `mutableClassDefBy(classDef)` ដើម្បីបាន `MutableClass`
- រក `MutableMethod` ត្រូវគ្នា
- ប្រើ `v0` ជានិច្ច

---

## 🐛 កំហុសទី 2: Release (semantic-release) បរាជ័យ

### មូលហេតុ

- `gradle.properties` មាន `version = 1.1.1` ខណៈ tags `v1.2.0`, `v1.3.0`, `v1.4.0` ត្រូវបានបង្កើតដោយដៃ (manual tag) នៅលើ commits `feat`
- semantic-release ព្យាយាមបង្កើត `v1.2.0` ឡើងវិញ → GitHub API បរាជ័យព្រោះ tag មានរួច
- បន្ថែម `Build .mpp bundle` បរាជ័យ → `Release` step មិនបានរត់ (skipped) ឬ `generatePatchesList` បរាជ័យព្រោះ .mpp មិនមាន

### ✅ ដំណោះស្រាយ

- Sync `gradle.properties` → `version = 1.4.0` ឲ្យត្រូវនឹង tag ចុងក្រោយ
- ជួសជុល Build .mpp bundle (ខាងលើ) → Release អាច build `.mpp` ថ្មីជាមួយ version ថ្មី
- Run `34945439049` បន្ទាប់ពីជួសជុល Build → `Release` ជោគជ័យ → បង្កើត `v1.4.1`
- Run `34946437108` → `v1.4.2` + Back-merge ជោគជ័យ

---

## 🐛 កំហុសទី 3: Back-merge main → dev បរាជ័យ

### មូលហេតុ

`release.yml` step `Back-merge main into dev`:

```bash
git merge --no-commit --no-ff origin/main
# អនុញ្ញាត conflict តែក្នុង generated_files:
# CHANGELOG.md, README.md, gradle.properties, patches-bundle.json, patches-list.json
# បើ conflict ក្នុង file ផ្សេង → abort → failure
```

- `main` មាន SimSimi patches (6) + swiftkey 2
- `dev` មាន offline voice + text editing toolbar (5 swiftkey) + NDK/cmake
- `extensions/proguard-rules.pro`, `extensions/swiftkey/build.gradle.kts`, `patches/...` មាន conflict មិនមែន generated → back-merge បរាជ័យ

### ✅ ដំណោះស្រាយ

Merge ដោយដៃ `main` → `dev`៖

```bash
git checkout dev
git merge main --no-commit
# - CHANGELOG, README, patches-bundle/list, gradle.properties → keep dev (HEAD)
#   gradle.properties → rm (dev មិន track)
# - proguard → keep main's fixed version (keep all extensions)
# - swiftkey/build.gradle.kts → merge: extension name + NDK/cmake + exportVoiceNativeLibraries
# - simsimi files → keep (add SimSimi to dev)
git commit -m "chore: back-merge main into dev [skip ci]"
git push origin dev
```

- Run `34946437108` បន្ទាប់ពី manual back-merge → Back-merge ជោគជ័យ (dev បាន update ទៅ 9a70e84)

---

## 📊 លទ្ធផលក្រោយជួសជុល

| Run ID | Branch | Conclusion | Steps ជោគជ័យ |
|--------|--------|------------|--------------|
| 34943884960 | main | failure | Build .mpp ❌ |
| 34944847096 | main | failure | Build .mpp ❌ |
| 34945439049 | main | failure | Build ✅, Release ✅, Back-merge ❌ |
| **34946437108** | **main** | **success** | **Build ✅, Verify ✅, Release ✅, Attest ✅, Upload ✅, Back-merge ✅** |

- Tag `v1.4.1` និង `v1.4.2` ត្រូវបានបង្កើតដោយជោគជ័យ
- `dev` branch បាន back-merge ដោយស្វ័យប្រវត្តិទៅ `9a70e84`
- Bundle `.mpp` ផ្ទៀងផ្ទាត់មាន `classes*.dex` និង `.mpe` (swiftkey + simsimi)

---

## 🛠️ ឯកសារដែលបានកែ

1. `extensions/proguard-rules.pro` — keep all extensions, dontobfuscate
2. `extensions/swiftkey/build.gradle.kts` — add `extension { name = ... }` + keep NDK config
3. `extensions/simsimi/build.gradle.kts` — add `extension { name = ... }`
4. `patches/src/main/kotlin/hooman/morphe/patches/simsimi/ads/RemoveAdsPatch.kt` — use mutableClassDefBy + v0
5. `patches/src/main/kotlin/hooman/morphe/patches/simsimi/membership/UnlockMembershipPatch.kt` — use mutableClassDefBy + v0
6. `gradle.properties` — sync version 1.1.1 → 1.4.0 → 1.4.2 (auto via release)

---

## 💡 មេរៀន

- ពេលបន្ថែម extension ថ្មី ត្រូវ update `proguard-rules.pro` ឲ្យ keep package ថ្មី
- `getAllClassesWithStrings()` ត្រឡប់ immutable → ត្រូវ `mutableClassDefBy` មុន `addInstructions`
- ប្រើ `v0` ជានិច្ចសម្រាប់ return value, កុំ overwrite `p0` (this)
- `gradle.properties` version ត្រូវ sync ជាមួយ tags — កុំបង្កើត tags ដោយដៃដោយមិន update gradle.properties
- Back-merge script អនុញ្ញាតតែ conflict ក្នុង generated files — បើ main/dev diverge ខ្លាំង ត្រូវ merge ដោយដៃ

---

**ជួសជុលដោយ:** arena agent  
**កាលបរិច្ឆេទ:** 2026-09-15  
**Branch:** `arena/01a0a410-swiftkey` → merged to `main` as `v1.4.2`
