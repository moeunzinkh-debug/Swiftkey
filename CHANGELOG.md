## [1.8.3](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.8.2...v1.8.3) (2026-09-18)

### 🐛 Bug Fixes

* **ci:** authenticate the back-merge clone so its push reaches dev ([b0fdd15](https://github.com/moeunzinkh-debug/Swiftkey/commit/b0fdd1560d3b55387aee19f5f46bd755bd4a7383))

## [1.8.2](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.8.1...v1.8.2) (2026-09-18)

### 🐛 Bug Fixes

* **ci:** stop .gitignore from blocking the main-to-dev back-merge ([ebe4a05](https://github.com/moeunzinkh-debug/Swiftkey/commit/ebe4a05b4d9886dca32fae49a25557862f2356a3))

## [1.8.1](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.8.0...v1.8.1) (2026-09-18)

### 🐛 Bug Fixes

* **swiftkey:** never pick our own injected screen as the launcher ([61e3aa0](https://github.com/moeunzinkh-debug/Swiftkey/commit/61e3aa01c270146952026b304764f38c9e22fbaa))
* **swiftkey:** resolve the launcher activity across manifest shapes ([7b19351](https://github.com/moeunzinkh-debug/Swiftkey/commit/7b193514dc13701e49893cb57f2bf2e779259d53))

## [1.8.0](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.7.0...v1.8.0) (2026-09-18)

### 🐛 Bug Fixes

* draw toolbar/settings icons with Bitmap+Canvas instead of VectorDrawable.Builder ([b4238c1](https://github.com/moeunzinkh-debug/Swiftkey/commit/b4238c168434e6c2be1edd1f51b4abf458aa8ae4))
* use Morphe MutableMethod proxy and correct BuilderInstruction35c arity ([c93d5f3](https://github.com/moeunzinkh-debug/Swiftkey/commit/c93d5f3e843e635017c4d504300534a84d608252))

### ✨ New Features

* add Patches settings entry and move Text editing button into the native toolbar row ([00dbf10](https://github.com/moeunzinkh-debug/Swiftkey/commit/00dbf104217d56b6ea8f8bba690e67627dc88fc7))
* Patches settings screen with offline mic settings + mic icon in the toolbar ([bbde329](https://github.com/moeunzinkh-debug/Swiftkey/commit/bbde3297753002827544f6f756ca594219ef0ace))

## [1.7.0](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.6.1...v1.7.0) (2026-09-16)

### ⚠ BREAKING CHANGES

* remove all Lucky Patcher patches

### 🐛 Bug Fixes

* remove invalid ApkProtection stub and use verified patcher APIs ([e475fd0](https://github.com/moeunzinkh-debug/Swiftkey/commit/e475fd0d4c64c3ac41474a37b295e803b2bb56b0))

### ✨ New Features

* remove all Lucky Patcher patches ([cb8d3a1](https://github.com/moeunzinkh-debug/Swiftkey/commit/cb8d3a19a3940292b050c2b5c6781c07958e9f1b))
* **swiftkey:** keep Disable telemetry, Fix mic, Text editing toolbar + APK crash protection ([2131bf1](https://github.com/moeunzinkh-debug/Swiftkey/commit/2131bf15cddab600e2919fcf17b1faa34c101fa3))

## [1.6.1](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.6.0...v1.6.1) (2026-09-16)

### 🐛 Bug Fixes

* **luckypatcher:** support obfuscated package variant ([764497e](https://github.com/moeunzinkh-debug/Swiftkey/commit/764497e5a0569d5e836e01d24db831eb3c289134))

## [1.6.0](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.5.0...v1.6.0) (2026-09-16)

### 🐛 Bug Fixes

* **ci:** guard README patch markers and merge duplicate app sections ([3cc7653](https://github.com/moeunzinkh-debug/Swiftkey/commit/3cc765341eed6548f9bc4dd5855e5d7cb4b05abe))
* **ci:** keep dev's rewritten MicSupport.java when back-merging main into dev ([0e2ad72](https://github.com/moeunzinkh-debug/Swiftkey/commit/0e2ad7267d0127a8bfb9939975ea8d3676692798))
* **ci:** keep the expected-failure back-merge tests from painting the run red ([d9eaccb](https://github.com/moeunzinkh-debug/Swiftkey/commit/d9eaccb54e70078852a689d33e1a1059949501fc)), closes [#19](https://github.com/moeunzinkh-debug/Swiftkey/issues/19)

### ✨ New Features

* **luckypatcher:** declare Lucky Patcher 12.10.8 as a supported target ([d3759a1](https://github.com/moeunzinkh-debug/Swiftkey/commit/d3759a190c416b223201b52f0cb23e4dd045768b))

## [1.5.0](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.4.4...v1.5.0) (2026-09-15)

### 🐛 Bug Fixes

* **luckypatcher:** use BytecodePatchContext and verified patcher APIs to fix compile errors ([4fae08d](https://github.com/moeunzinkh-debug/Swiftkey/commit/4fae08d408b97eda489d04e51edbaf2c05ae2e1e))

### ✨ New Features

* **luckypatcher:** add Lucky Patcher tag with unlock pro/premium/membership/vip + APK protection ([a54bc86](https://github.com/moeunzinkh-debug/Swiftkey/commit/a54bc86158a64b77f376f05adb19aed708c6b169))
* **luckypatcher:** add new tag Lucky Patcher with unlock pro/premium/membership/vip ([6363a69](https://github.com/moeunzinkh-debug/Swiftkey/commit/6363a6963c9517b76bc402bf522ca718fdf13d2f))
* **protection:** add APK protection system to prevent corruption during patch ([aa55eb4](https://github.com/moeunzinkh-debug/Swiftkey/commit/aa55eb4c75627152f362113dae9fea48709666b4))

## [1.4.4](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.4.3...v1.4.4) (2026-09-15)

### 🐛 Bug Fixes

* **swiftkey:** stop the dead Vosk model download that 404s for Khmer/Thai ([93e5203](https://github.com/moeunzinkh-debug/Swiftkey/commit/93e520349629d0e9732c3fe602feb080876e8d0d)), closes [12/#13](https://github.com/12/Swiftkey/issues/13)

## [1.4.3](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.4.2...v1.4.3) (2026-09-15)

### 🐛 Bug Fixes

* **simsimi:** avoid smali compile crash in microg account hook ([22dd4ca](https://github.com/moeunzinkh-debug/Swiftkey/commit/22dd4ca1e5efa836d7cdd9395eeb7a8bd811a6a6))

## [1.4.2](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.4.1...v1.4.2) (2026-09-15)

### 🐛 Bug Fixes

* trigger release to test back-merge after manual fix ([bf9126e](https://github.com/moeunzinkh-debug/Swiftkey/commit/bf9126eba2b624f50e7e0461caf47c4ed964d992))

## [1.4.1](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.4.0...v1.4.1) (2026-09-15)

### 🐛 Bug Fixes

* compile errors in SimSimi patches - use mutableClassDefBy for getAllClassesWithStrings ([2dbe431](https://github.com/moeunzinkh-debug/Swiftkey/commit/2dbe4314fdf7d0c4242434f9cd260dd234c7c6a4))
* proguard keep all extensions, explicit .mpe names, sync version to 1.4.0 ([8708e06](https://github.com/moeunzinkh-debug/Swiftkey/commit/8708e066811def9f44c914ae1819b52b2e291cbc))

## [1.1.1](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.1.0...v1.1.1) (2026-09-15)

### 🐛 Bug Fixes

* resolve modify/delete conflicts in the main→dev back-merge ([326dcc1](https://github.com/moeunzinkh-debug/Swiftkey/commit/326dcc122ddfe1672ab3db07935e6ddf25b11255))

## [1.1.0](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.0.1...v1.1.0) (2026-09-15)

### ✨ New Features

* **voice:** add offline models support for Khmer, English, Thai, and Chinese ([12cbfcf](https://github.com/moeunzinkh-debug/Swiftkey/commit/12cbfcfc1358e8da4cc54729926092d256a8366c))
* **voice:** add on-demand Vosk offline voice model manager ([9a6c1dd](https://github.com/moeunzinkh-debug/Swiftkey/commit/9a6c1dd1549eb3d5f73f35a42743e0a0a754abf4))

## [1.0.1](https://github.com/moeunzinkh-debug/Swiftkey/compare/v1.0.0...v1.0.1) (2026-09-14)

### 🐛 Bug Fixes

* abstract onCreate crash, SwiftKey 9.13.14.5 target, reliable .mpp release CI ([#3](https://github.com/moeunzinkh-debug/Swiftkey/issues/3)) ([b839622](https://github.com/moeunzinkh-debug/Swiftkey/commit/b8396223e0e9539b8e86cbf61bfb4f5eafc3e72a))

## 1.0.0 (2026-09-14)

### ✨ New Features

* SwiftKey-only Morphe patches with microphone fix & MicroG RE GmsCore support ([#1](https://github.com/moeunzinkh-debug/Swiftkey/issues/1)) ([9752544](https://github.com/moeunzinkh-debug/Swiftkey/commit/97525443b7ae09c022e73692bac73575880f63d0))

# Changelog

ឯកសារនេះ​ត្រូវ​បាន​ semantic-release បន្ទាន់​សម័យ​ដោយ​ស្វ័យប្រវត្តិ​នៅ​ពេល​ចេញផ្សាយ
(យោង​តាម​សារ commit បែប conventional commits៖ `feat:`, `fix:`, ...)។
