#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
output="build/feature-tests"
mkdir -p "$output"
source="extensions/swiftkey/src/main/java/app/morphe/extension/swiftkey"
"${JAVAC:-javac}" -encoding UTF-8 -d "$output" \
  "$source/voice/VoiceLanguage.java" \
  "$source/voice/ModelDownload.java" \
  "$source/toolbar/TextEditingActions.java" \
  .github/tests/FeatureTests.java
"${JAVA:-java}" -cp "$output" FeatureTests
python3 .github/scripts/test-feature-scope.py
