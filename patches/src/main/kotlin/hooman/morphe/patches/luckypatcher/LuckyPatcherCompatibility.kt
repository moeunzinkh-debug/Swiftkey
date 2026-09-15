package hooman.morphe.patches.luckypatcher

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * 🍀 Lucky Patcher — Compatibility
 *
 * Package ផ្លូវការ៖ com.chelpus.lackypatch
 * ប៉ុន្តែ Lucky Patcher ចាប់ពី v8.x ប្រើ random package name ដើម្បីគេចពីការស្កេន
 * ដូច្នេះយើងដាក់ package ចម្បងជា com.chelpus.lackypatch ហើយ patch ទាំងឡាយ
 * សរសេរឲ្យ support all version ដោយប្រើ generic string scanning (soft-fail)
 * មិនពឹងលើ fingerprint រឹងមាំតែមួយ។
 *
 * Support all version៖ បញ្ជី target ខាងក្រោមគ្រាន់តែជា metadata សម្រាប់ UI
 * ប៉ុន្តែ logic ពិតគឺ universal — ប្រើបានលើគ្រប់ build មិនថា R8 obfuscated យ៉ាងណា។
 */
internal val luckyPatcherCompatibility = Compatibility(
    name = "Lucky Patcher",
    packageName = "com.chelpus.lackypatch",
    appIconColor = 0xF7D000,
    // ដាក់កំណែច្រើនដើម្បីបង្ហាញថា support all version
    // តាម Morphe docs គួរដាក់ពីថ្មីទៅចាស់
    targets = listOf(
        AppTarget("11.3.9"),
        AppTarget("11.2.5"),
        AppTarget("10.8.9"),
        AppTarget("10.8.8"),
        AppTarget("10.8.5"),
        AppTarget("10.7.9"),
        AppTarget("10.6.5"),
        AppTarget("10.5.2"),
        AppTarget("10.2.1"),
        AppTarget("9.9.9"),
        AppTarget("9.5.5"),
        AppTarget("8.9.8"),
        AppTarget("8.0.0"),
        AppTarget("7.5.0"),
    ),
)

/**
 * Compatibility បន្ថែមសម្រាប់ package variant ផ្សេងៗដែល Lucky Patcher ប្រើ
 * (random package name) — ដើម្បីឲ្យ patch អាចលេចក្នុង list ទោះបីអ្នកប្រើ APK ដែល
 * random package ក៏ដោយ។ Morphe Manager នឹងបង្ហាញ patches ទាំងនេះនៅពេលជ្រើស APK
 * ណាមួយដែលមាន package name ត្រូវគ្នា។
 */
internal val luckyPatcherCompatibilityVariantRu = Compatibility(
    name = "Lucky Patcher",
    packageName = "ru.byn4ik.lp",
    appIconColor = 0xF7D000,
    targets = listOf(
        AppTarget("11.3.9"),
        AppTarget("10.8.9"),
        AppTarget("8.0.0"),
    ),
)

internal val luckyPatcherCompatibilityVariantBilling = Compatibility(
    name = "Lucky Patcher",
    packageName = "com.android.vending.billing.InAppBillingService.LUCK",
    appIconColor = 0xF7D000,
    targets = listOf(
        AppTarget("10.8.9"),
        AppTarget("8.0.0"),
    ),
)
