# ⌨️ SwiftKey Morphe Patches

បណ្តុំ​បំណះ (patches) ផ្ទាល់ខ្លួន​សម្រាប់​ **Microsoft SwiftKey Keyboard**
ដើម្បី​ប្រើ​ជាមួយ​កម្មវិធី [Morphe](https://morphe.software) (Morphe Manager) នៅលើ Android។

> 🙏 បំណះ​ដើម​រៀបចំ​ដោយ [arandomhooman/hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches)
> ដែល​បន្ត​ពី​ស្នាដៃ [ReVanced](https://github.com/ReVanced)។ គម្រោង​នេះ​យក​តែ​ផ្នែក SwiftKey មក​ចាប់ផ្តើម
> ហើយ​ចែកចាយ​ក្រោម​អាជ្ញាប័ណ្ណ​ដដែល​គឺ **GPLv3**។

## 🩹 បញ្ជី​បំណះ

<!-- PATCHES_START EXPANDED -->
> **[v1.6.0](https://github.com/moeunzinkh-debug/Swiftkey/releases/tag/v1.6.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;12 patches total
<details>
<summary>📦 Microsoft SwiftKey&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.13.14.5 | 9.13.13.5 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable telemetry](#disable-telemetry) | Stops SwiftKey's first-party telemetry records and uploads, Adjust attribution, Crashlytics, Firebase Sessions, legacy Google Analytics, and app exception reporting. Push messaging and the multipurpose job service stay enabled. |  |
| [Fix microphone voice input](#fix-microphone-voice-input) | Makes the microphone button work like on Gboard without installing Google: SwiftKey's speech checks stop hard-requiring the Google app and any Android RecognitionService (the system default, e.g. Kõnele/Vosk offline or Speech Recognition & Synthesis) is used. Install and set a speech recognition provider in system settings first; the patch only removes the Google gate, it does not ship a speech engine. |  |

</details>

<details>
<summary>📦 SimSimi&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**🎯 Supported versions:**

| 9.1.9 | 9.1.8 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [GmsCore Bytecode Redirect (MicroG RE)](#gmscore-bytecode-redirect-microg-re) | Rewire all Google Play Services references to MicroG RE's GmsCore package `app.revanced.android.gms`: GMS package-name string constants, account vendor strings, c2dm/GMS permissions and provider authorities. Keep service intent ACTIONS literal (GmsCore serves them under the original names). |  |
| [GmsCore Signature and Availability Bypass](#gmscore-signature-and-availability-bypass) | Neutralize the bundled play-services-basement availability/enforce checks: the enforce check returns immediately and the availability result reads SUCCESS (0), so the app runs when GmsCore replaces real GMS. Checks absent from this build must be soft-skipped, not fatal. |  |
| [GmsCore support (MicroG RE)](#gmscore-support-microg-re) | Umbrella patch pulling in 2+3+4 plus the shared manifest patch: <queries> visibility for GmsCore/speech services, account permissions, c2dm permission rename. Option: "Original signing certificate SHA-1" (cloned builds only). Requires MicroG RE 7.x installed. | • Original signing certificate SHA-1 |
| [MicroG Account Permissions](#microg-account-permissions) | Request GmsCore account permissions at runtime once (GET_ACCOUNTS and `app.revanced.gms.EXTENDED_ACCESS`) the first time any app activity starts, using a structural Activity-superclass walk (R8-name independent). No-op when no GmsCore is installed. |  |
| [Remove ads](#remove-ads) | Removes ads, banners, interstitials, rewarded ads from SimSimi. Blocks AdMob, Unity Ads, AppLovin, IronSource and other ad SDKs client-side. |  |
| [Unlock membership](#unlock-membership) | Forces the VIP flag to true so premium features are unlocked client-side. Server-validated assets (cloud effects/templates) are not affected. |  |

</details>

<details>
<summary>📦 Lucky Patcher&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 12.10.8 | 11.3.9 | 11.2.5 | 10.8.9 | 10.8.8 | 10.8.5 | 10.7.9 | 10.6.5 | 10.5.2 | 10.2.1 | 9.9.9 | 9.5.5 | 8.9.8 | 8.0.0 | 7.5.0 |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Membership (Lucky Patcher)](#unlock-membership-lucky-patcher) | Unlocks Membership/Subscription (isMember, membership, isSubscribed) by forcing checks to true and tier to 1. Includes APK Protection: backup/rollback, DEX validation, system class guard. Supports all versions via generic scanning. Tag: Lucky Patcher. |  |
| [Unlock Premium (Lucky Patcher)](#unlock-premium-lucky-patcher) | Unlocks Premium features (isPremium, premium_user, has_premium) by forcing checks to true and level to 1. Includes APK Protection: backup/rollback, DEX validation, system class guard. Supports all versions via generic scanning. Tag: Lucky Patcher. |  |
| [Unlock Pro (Lucky Patcher)](#unlock-pro-lucky-patcher) | Unlocks Pro features (isPro, pro_user, pro_version) by forcing boolean checks to true and level to 1. Includes APK Protection: backup/rollback, DEX validation, system class guard. Supports all versions via generic scanning. Tag: Lucky Patcher. |  |
| [Unlock VIP / Pro / Premium / Membership (Lucky Patcher)](#unlock-vip-pro-premium-membership-lucky-patcher) | All-in-one unlock for VIP, Pro, Premium, Membership via Lucky Patcher style. Forces all VIP/Pro/Premium/Membership boolean checks to true, levels to 1, and bypasses billing/license checks. Includes APK Protection: backup/rollback, DEX validation, system class guard, 50 patches/class limit. Supports ALL versions via generic scanning — no hard fingerprint, soft-fail safe. Tag: Lucky Patcher. |  |

</details>

<!-- PATCHES_END -->

## 📜 អាជ្ញាប័ណ្ណ និង​សម្គាល់​ស្នាដៃ

- កូដបំណះ៖ **GPLv3** (យក​ពី [hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches))។
- ឈ្មោះ **Morphe** ជា​កម្មសិទ្ធិ​របស់​គម្រោង Morphe — សូម​មើល `NOTICE` (forks ត្រូវ​ប្រើ​ឈ្មោះ​ផ្ទាល់ខ្លួន;
  ហៅ​ខ្លួនឯង​ថា "compatible with Morphe" បាន​តែ​ក្នុង​ន័យ​ពណ៌នា)។
- repo នេះ​មិន​ចែកចាយ APK របស់ Microsoft ទេ — អ្នកប្រើ​ត្រូវ​ត្រៀម APK ដោយ​ខ្លួនឯង។
# Test
