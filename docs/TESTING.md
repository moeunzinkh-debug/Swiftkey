# Device / APK acceptance checklist

The source is experimental until this checklist passes on real SwiftKey APKs. JVM command tests,
Java compilation, or C++ syntax checks alone do not validate an IME, native ABI loading or speech accuracy.
Do not publish a release claiming otherwise.

## Build and packaging

- JDK 21, SDK 36, NDK 28.2.13676358, CMake 3.22.1, Git.
- Run `bash .github/scripts/test-features.sh`.
- Run `./gradlew :patches:buildAndroid generatePatchesList --no-daemon`.
- Run `python3 .github/scripts/verify-feature-bundle.py <bundle.mpp>`.
- Catalogue contains exactly **Text editing toolbar** and **Offline microphone**. No selectable helper patches.
- Patch original complete APKs of 9.13.13.5 and 9.13.14.5. Do not distribute those APKs in this repository.
- Test text-editing-only, microphone-only, and both. Only selected tools should be visible.
- Verify patched APK contains `lib/<original-ABI>/libswiftkey_whisper.so` and third-party notices.
  Do not add an ABI that SwiftKey itself did not ship. Missing ABI splits must produce a patch error.
- Verify the speech service runs in the same process as the IME (including a private `:keyboard` process).
- Confirm only microphone selection adds RECORD_AUDIO / INTERNET permissions, speech service and permission activity.
  No account permissions, Google permission rewrites, GmsCore queries, or clone signature options are injected.
- Confirm a high-register-count `onCreateInputView` is preserved behind the small wrapper, not rewritten inline.
- Confirm unsupported/final inherited lifecycle methods produce an actionable patch error, not an invalid APK.

## Text editing

- Toolbar appears above the original keyboard. Native keys, suggestions, original toolbar and swipe typing still work.
- Portrait/landscape, light/dark mode, floating/one-handed/fullscreen IME, large font, TalkBack, keyboard hide/show.
- Added toolbar and expanded panels accept touches even when SwiftKey uses a custom touchable-insets region.
- Test EditText, a browser contenteditable field and a Compose editor. Arrows/Home/End work where the editor supports them.
- Select mode extends a selection; selecting again turns it off. Select all/Cut/Copy/Paste use the active editor.
- Undo/Redo use the application's history. An unsupported action must not report success or maintain a separate text log.
- Changing fields/apps or hiding the keyboard clears selection mode. Cut/Copy must not export password text.

## Download on demand

- Fresh install: opening the keyboard, opening Offline mic and choosing any language does **not** start a download.
- UI shows exactly Khmer, English, Thai, Chinese. The default is visibly selected; an unsupported recognition request fails.
- Download button discloses 142 MiB. Download is HTTPS, progress is visible and Cancel works (network read timeout is 15s).
- Cancel mid-download, disconnect network, kill the process, run out of storage, corrupt a downloaded fixture.
  No partial file is treated as ready; retry works and a previously good model is not overwritten on failure.
- Check SHA-256 `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe` for the installed model.
- After one successful download, all four language choices reuse the same model. Restarting SwiftKey preserves it.
- Clear app data/uninstall removes it. No model/audio files should appear in shared storage or Android backups.

## Offline dictation and privacy

- Airplane mode with the model installed: Start → speak → Stop → text is inserted without a network request.
- Test short and 30-second utterances in each of the four languages. Measure latency, RAM use and actual accuracy;
  especially Khmer/Thai accuracy must be evaluated, not inferred from the language selector.
- Test arm64-v8a and armeabi-v7a devices; x86/x86_64 emulators where supported. Include an Android 16 KiB page-size device.
- Grant/deny/revoke microphone permission. The permission activity must never start recording automatically.
- Android privacy microphone indicator must turn off on Stop, Cancel, keyboard hide, field/app change and service destruction.
- Cancel while loading the model or decoding. No late result should reach a different field or application.
- A silent recording yields no text. Audio is limited to 30 seconds, held only in memory and is not logged/uploaded/saved.
- Password/visible-password/numeric-password fields refuse offline dictation.
- Try no model, low RAM, failed native library initialization, audio device unavailable and recognizer client disconnect.
  SwiftKey should remain usable; report an error rather than claim an offline result.
- Original microphone: test with Multi-modal/Azure voice disabled and Google app absent. The Android SpeechRecognizer
  calls should use the in-APK service. Azure voice is intentionally not redirected; the new Offline mic is independent.

## Release state

`patches-bundle.json` still points to the previously published v1.1.1 artifact. It is historical release metadata,
not an artifact containing these source changes. Let semantic-release create a new version after validation;
do not overwrite old assets or point users to v1.1.1 as if it contained the new engine.
