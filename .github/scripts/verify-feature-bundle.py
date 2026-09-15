#!/usr/bin/env python3
"""Check an ACTUAL .mpp, including the native payload that .mpe alone cannot carry."""
from pathlib import Path
import sys
import zipfile

if len(sys.argv) < 2:
    raise SystemExit("Usage: verify-feature-bundle.py <bundle.mpp> [...]")

ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
for filename in sys.argv[1:]:
    with zipfile.ZipFile(filename) as bundle:
        names = set(bundle.namelist())
        assert any(Path(n).name.startswith("classes") and n.endswith(".dex") for n in names), "Missing patch DEX"
        assert "extensions/swiftkey.mpe" in names, "Missing SwiftKey extension"
        expected = {f"lib/{abi}/libswiftkey_whisper.so" for abi in ABIS}
        actual = set(bundle.read("native/swiftkey/index.txt").decode().splitlines())
        assert actual == expected, f"Wrong native library index: {actual}"
        for entry in expected:
            library = bundle.read(f"native/swiftkey/{entry}")
            assert library.startswith(b"\x7fELF"), f"Not an ELF native library: {entry}"
            assert len(library) > 10000, f"Empty/stub native library: {entry}"
        assert "native/swiftkey/THIRD_PARTY_NOTICES.txt" in names, "Missing voice license notices"
        assert not any(Path(n).name.startswith("ggml-") and n.endswith(".bin") for n in names), "Do not bundle model weights"
        print(f"Verified offline voice payload: {filename}")
