# ⌨️ SwiftKey Morphe Patches

បណ្តុំ​បំណះ (patches) ផ្ទាល់ខ្លួន​សម្រាប់​ **Microsoft SwiftKey Keyboard**
ដើម្បី​ប្រើ​ជាមួយ​កម្មវិធី [Morphe](https://morphe.software) (Morphe Manager) នៅលើ Android។

> 🙏 បំណះ​ដើម​រៀបចំ​ដោយ [arandomhooman/hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches)
> ដែល​បន្ត​ពី​ស្នាដៃ [ReVanced](https://github.com/ReVanced)។ គម្រោង​នេះ​យក​តែ​ផ្នែក SwiftKey មក​ចាប់ផ្តើម
> ហើយ​ចែកចាយ​ក្រោម​អាជ្ញាប័ណ្ណ​ដដែល​គឺ **GPLv3**។

## 🩹 បញ្ជី​បំណះ

<!-- PATCHES_START EXPANDED -->
> **[v1.8.3](https://github.com/moeunzinkh-debug/Swiftkey/releases/tag/v1.8.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 Microsoft SwiftKey&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.13.14.5 | 9.13.13.5 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable telemetry](#disable-telemetry) | Stops SwiftKey's first-party telemetry records and uploads, Adjust attribution, Crashlytics, Firebase Sessions, legacy Google Analytics, and app exception reporting. Push messaging and the multipurpose job service stay enabled. |  |
| [Fix microphone voice input](#fix-microphone-voice-input) | Makes the microphone button work like on Gboard without installing Google: SwiftKey's speech checks stop hard-requiring the Google app and any Android RecognitionService (the system default, e.g. Kõnele/Vosk offline or Speech Recognition & Synthesis) is used. Install and set a speech recognition provider in system settings first; the patch only removes the Google gate, it does not ship a speech engine. |  |
| [Patches](#patches) | Adds a Patches entry to the SwiftKey settings screen. Opens the Patches settings screen: the patches applied to this build (only the selected ones), plus the offline microphone settings (default/installed engines, offline mode toggle, engine switch and the Kõnele offline engine installer). |  |
| [Text editing toolbar](#text-editing-toolbar) | Adds a collapsible text editing toolbar inside the keyboard: cursor arrows, Home/End, selection, Select all, Cut, Copy, Paste, Undo and Redo. Actions use the current editor; Undo/Redo depend on that app's support. Does not change login, telemetry or Google services. |  |

</details>

<!-- PATCHES_END -->

## 📜 អាជ្ញាប័ណ្ណ និង​សម្គាល់​ស្នាដៃ

- កូដបំណះ៖ **GPLv3** (យក​ពី [hoomans-morphe-patches](https://github.com/arandomhooman/hoomans-morphe-patches))។
- ឈ្មោះ **Morphe** ជា​កម្មសិទ្ធិ​របស់​គម្រោង Morphe — សូម​មើល `NOTICE` (forks ត្រូវ​ប្រើ​ឈ្មោះ​ផ្ទាល់ខ្លួន;
  ហៅ​ខ្លួនឯង​ថា "compatible with Morphe" បាន​តែ​ក្នុង​ន័យ​ពណ៌នា)។
- repo នេះ​មិន​ចែកចាយ APK របស់ Microsoft ទេ — អ្នកប្រើ​ត្រូវ​ត្រៀម APK ដោយ​ខ្លួនឯង។
# Test
