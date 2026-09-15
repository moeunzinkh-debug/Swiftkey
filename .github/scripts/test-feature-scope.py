#!/usr/bin/env python3
"""Source/catalogue regression tests. These do NOT replace Android/NDK or device testing."""
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "patches/src/main/kotlin/hooman/morphe/patches/swiftkey"
EXTENSIONS = ROOT / "extensions/swiftkey/src/main/java/app/morphe/extension/swiftkey"
EXPECTED = {"Text editing toolbar", "Offline microphone"}


def source_descriptions():
    patches = {}
    declaration = re.compile(
        r'\b(?:bytecodePatch|resourcePatch)\(\s*name\s*=\s*("(?:\\.|[^"\\])*")\s*,'
        r'\s*description\s*=\s*(.*?)\n\)', re.S
    )
    for path in SOURCES.rglob("*.kt"):
        for match in declaration.finditer(path.read_text()):
            name = json.loads(match[1])
            description = "".join(json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"', match[2]))
            if name in patches:
                raise AssertionError(f"Duplicate named patch: {name}")
            patches[name] = description
    return patches


class FeatureScopeTests(unittest.TestCase):
    def test_only_two_named_patches(self):
        self.assertEqual(set(source_descriptions()), EXPECTED)
        named_count = sum(len(re.findall(r'\b(?:bytecodePatch|resourcePatch)\(\s*name\s*=', p.read_text()))
                          for p in SOURCES.rglob("*.kt"))
        self.assertEqual(named_count, 2)

    def test_catalogue_matches_source(self):
        catalogue = json.loads((ROOT / "patches-list.json").read_text())
        self.assertEqual({p["name"]: p["description"] for p in catalogue["patches"]}, source_descriptions())
        self.assertEqual(len(catalogue["patches"]), 2)
        for patch in catalogue["patches"]:
            self.assertEqual(patch["dependencies"], ["BytecodePatch", "ResourcePatch"])
            self.assertEqual(patch["options"], [])
            self.assertEqual(patch["compatiblePackages"][0]["packageName"], "com.touchtype.swiftkey")
            self.assertTrue(all(t["isExperimental"] for t in patch["compatiblePackages"][0]["targets"]))
            self.assertTrue(all(t["minSdk"] == 26 for t in patch["compatiblePackages"][0]["targets"]))

    def test_unrelated_implementations_removed(self):
        for directory in ("login", "privacy", "microg"):
            self.assertFalse((SOURCES / directory).exists(), directory)
        self.assertFalse((EXTENSIONS / "GmsSupport.java").exists())
        self.assertFalse((EXTENSIONS / "VoskModelManager.java").exists())
        production = "\n".join(p.read_text() for base in (SOURCES, EXTENSIONS)
                               for p in base.rglob("*") if p.suffix in (".java", ".kt"))
        for removed in ("swiftKeySupportManifestPatch", "unsupportedSwiftKeyVersion", "spoofedSignatureProvider",
                        "GmsSupport", "android.permission.GET_ACCOUNTS", "app.revanced.android.gms"):
            self.assertNotIn(removed, production)

    def test_exact_language_choices(self):
        language = (EXTENSIONS / "voice/VoiceLanguage.java").read_text()
        self.assertEqual(re.findall(r'\b[A-Z]+\("([a-z]+)",', language), ["km", "en", "th", "zh"])
        # A download should be driven by the Download button, not by a language change or mic check.
        microphone = (EXTENSIONS / "MicSupport.java").read_text()
        self.assertNotIn(".download(", microphone)
        manager = (EXTENSIONS / "voice/OfflineModelManager.java").read_text()
        selection = manager.split("public static void selectLanguage", 1)[1].split("public static File", 1)[0]
        self.assertNotIn("download(", selection)

    def test_jni_entrypoints_and_notice(self):
        java = (EXTENSIONS / "voice/WhisperEngine.java").read_text()
        cpp = (ROOT / "extensions/swiftkey/src/main/cpp/whisper_jni.cpp").read_text()
        methods = set(re.findall(r'private static native [\w\[\]]+ (\w+)\(', java))
        exports = set(re.findall(r'Java_app_morphe_extension_swiftkey_voice_WhisperEngine_(\w+)\(', cpp))
        self.assertEqual(methods, exports)
        self.assertEqual(len(methods), 4)
        self.assertIn('System.loadLibrary("swiftkey_whisper")', java)
        download = (EXTENSIONS / "voice/ModelDownload.java").read_text()
        sha = re.search(r'SHA256 = "([a-f0-9]{64})"', download)[1]
        notice = (ROOT / "patches/src/main/resources/native/swiftkey/THIRD_PARTY_NOTICES.txt").read_text()
        self.assertIn(sha, notice)

    def test_generated_readme_is_in_sync(self):
        readme = (ROOT / "README.md").read_text()
        branch = re.search(r'&nbsp;&nbsp;•&nbsp;&nbsp;`([^`]+)`', readme)[1]
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "README.md"
            output.write_text(readme)
            subprocess.run([sys.executable, str(ROOT / ".github/scripts/generate_patches_readme.py"),
                            "moeunzinkh-debug/Swiftkey", branch, str(ROOT / "patches-list.json"), str(output)],
                           check=True, capture_output=True)
            self.assertEqual(output.read_text(), readme)


if __name__ == "__main__":
    unittest.main(verbosity=2)
