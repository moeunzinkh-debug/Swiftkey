#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Mirror + regression test for the SwiftKey "Patches" launcher-activity discovery.

Why this exists
---------------
`PatchesSettingsPatch.kt` used to fail the *whole* patch run with

    SwiftKey Patches: no launcher activity found in the manifest.

whenever its single strict check matched nothing. That check required one
`<intent-filter>` carrying BOTH `MAIN` and `LAUNCHER`, on a DIRECT child of
`<application>`, read only through the literal `android:` prefix. Discovery is
now tiered and degrades to a warning instead of throwing.

The patch module needs the Morphe artifacts from GitHub Packages to compile, so
this script mirrors the Kotlin decision logic 1:1 in Python and exercises it
against the manifest shapes that occur in real builds. It asserts that:

  1. the OLD algorithm fails on each documented shape (the bug was real),
  2. the NEW algorithm resolves the right activity on each of them (the fix works),
  3. the NEW algorithm still returns exactly what the OLD one did on shapes that
     already worked (no regression where nothing was broken),
  4. discovery degrades to "nothing" instead of raising,
  5. discovery survives both parser modes Morphe's DOM can produce,
  6. the mirror has not drifted from the Kotlin source.

Parser fidelity: Morphe builds its DOM with `DocumentBuilderFactory.newInstance()`
and never sets `namespaceAware`, so the default (false) applies - qualified names
are kept literally (`android:name`, tagName `activity`) and `localName` is null.
`_parse_unaware` reproduces exactly that. ElementTree's own parser is used for the
namespace-aware variant, which normalises to `{uri}name` and drops `localName`'s
prefix, exercising the `getAttributeNS` branch instead.

Run: python3 .github/scripts/test_launcher_discovery.py
Exits non-zero if any assertion fails.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
import xml.parsers.expat as expat
from dataclasses import dataclass, field
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)

ACTION_MAIN = "android.intent.action.MAIN"
CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
PACKAGE = "com.touchtype.swiftkey"
NS = "android"  # the prefix ToolbarManifestPatch.kt tries first

REPO_ROOT = Path(__file__).resolve().parents[2]
KOTLIN_PATCH = REPO_ROOT / "patches/src/main/kotlin/hooman/morphe/patches/swiftkey/settings/PatchesSettingsPatch.kt"
KOTLIN_HELPERS = REPO_ROOT / "patches/src/main/kotlin/hooman/morphe/patches/swiftkey/toolbar/ToolbarManifestPatch.kt"
SELF = Path(__file__).resolve()


# --------------------------------------------------------------------------------------
# A namespace-UNAWARE parser, matching Morphe's DocumentBuilderFactory defaults
# --------------------------------------------------------------------------------------

def _parse_unaware(text: str) -> ET.Element:
    """Parse keeping qualified names verbatim and leaving localName empty (DOM default)."""
    parser = expat.ParserCreate(namespace_separator=None)
    stack: list[ET.Element] = []
    root: list[ET.Element] = []

    def start(tag: str, attributes: dict[str, str]) -> None:
        # tagName keeps the qualified name verbatim; a namespace-unaware DOM reports localName
        # as null, which _element_local_name models by falling back to splitting the tag.
        element = ET.Element(tag, dict(attributes))
        if stack:
            stack[-1].append(element)
        else:
            root.append(element)
        stack.append(element)

    def end(_tag: str) -> None:
        stack.pop()

    parser.StartElementHandler = start
    parser.EndElementHandler = end
    parser.Parse(text, True)
    if not root:
        raise AssertionError("manifest fixture produced no root element")
    return root[0]


def parse_manifest(text: str, ns_aware: bool = False) -> ET.Element:
    return ET.fromstring(text) if ns_aware else _parse_unaware(text)


# --------------------------------------------------------------------------------------
# Mirror of the prefix-tolerant readers in ToolbarManifestPatch.kt
# --------------------------------------------------------------------------------------

def local(qualified: str | None) -> str:
    """localPart(): strip any prefix or namespace, leaving the local name."""
    if not qualified:
        return ""
    if qualified.startswith("{"):
        return qualified.split("}", 1)[1]
    return qualified.rsplit(":", 1)[-1]


def _element_local_name(element: ET.Element) -> str:
    """tagMatches(): prefers the DOM localName, falls back to splitting the tag."""
    return local(element.get("_localName")) or local(element.tag)


def attr(element: ET.Element, local_name: str) -> str:
    """attr(): android:-prefixed, then any prefix, then bare, then namespace-aware."""
    value = element.get(f"{NS}:{local_name}")
    if value:
        return value
    for key, raw in element.attrib.items():
        if key.startswith("{"):
            continue  # handled by the namespace-aware branch below
        if local(key) == local_name and raw:
            return raw
    value = element.get(local_name)
    if value:
        return value
    return element.get(f"{{{ANDROID_NS}}}{local_name}") or ""


def children(element: ET.Element, tag: str) -> list[ET.Element]:
    """children(): direct children matched by local name."""
    return [child for child in list(element) if _element_local_name(child) == tag]


def descendants(element: ET.Element, tag: str) -> list[ET.Element]:
    """descendants(): whole subtree matched by local name, in document order."""
    out: list[ET.Element] = []
    for child in list(element):
        if _element_local_name(child) == tag:
            out.append(child)
        out.extend(descendants(child, tag))
    return out


def flag_defaults_true(element: ET.Element, name: str) -> bool:
    """flagDefaultsTrue(): absent means true; only the literal "false" is off."""
    return attr(element, name).strip().lower() != "false"


def application_of(root: ET.Element) -> ET.Element:
    apps = children(root, "application")
    if len(apps) != 1:
        raise AssertionError(f"expected exactly one <application>, found {len(apps)}")
    return apps[0]


# --------------------------------------------------------------------------------------
# Mirror of the NEW discovery in PatchesSettingsPatch.kt
# --------------------------------------------------------------------------------------

@dataclass
class LauncherCandidate:
    tag: str
    class_name: str
    actions: set[str] = field(default_factory=set)
    categories: set[str] = field(default_factory=set)
    enabled: bool = True
    alias: bool = False

    @property
    def has_main(self) -> bool:
        return ACTION_MAIN in self.actions

    @property
    def has_launcher_category(self) -> bool:
        return CATEGORY_LAUNCHER in self.categories


def resolve_component_name(raw_name: str, package_name: str) -> str:
    if not raw_name.strip():
        return ""
    if raw_name.startswith("."):
        return package_name + raw_name
    if "." not in raw_name:
        return f"{package_name}.{raw_name}"
    return raw_name


def _read(application: ET.Element, tag: str, name_attribute: str, package_name: str) -> list[LauncherCandidate]:
    out: list[LauncherCandidate] = []
    for element in descendants(application, tag):
        filters = children(element, "intent-filter")
        actions = {attr(a, "name") for f in filters for a in children(f, "action") if attr(a, "name")}
        categories = {attr(c, "name") for f in filters for c in children(f, "category") if attr(c, "name")}
        out.append(LauncherCandidate(
            tag=tag,
            class_name=resolve_component_name(attr(element, name_attribute), package_name),
            actions=actions,
            categories=categories,
            enabled=flag_defaults_true(element, "enabled"),
            alias=(name_attribute == "targetActivity"),
        ))
    return out


def collect_components(application: ET.Element, package_name: str) -> list[LauncherCandidate]:
    activities = _read(application, "activity", "name", package_name)
    aliases = _read(application, "activity-alias", "targetActivity", package_name)
    if not any(alias.class_name for alias in aliases):
        # The decoder normalised the attribute: retry before dropping every alias.
        aliases = _read(application, "activity-alias", "name", package_name)
    return activities + aliases


def rank_launcher_candidates(components: list[LauncherCandidate]) -> list[LauncherCandidate]:
    named = [c for c in components if c.class_name.strip() and c.enabled]

    # Tier 1 - the Android launcher contract proper.
    strict = [c for c in named if c.has_launcher_category and c.has_main]
    if strict:
        return strict

    # Tier 2 - MAIN and LAUNCHER present but in separate intent-filters.
    relaxed = [c for c in named if c.has_launcher_category or c.has_main]
    if relaxed:
        return relaxed

    # Tier 3 - no launcher contract; prefer an alias, break ties by name, hook exactly one.
    return sorted(named, key=lambda c: (not c.alias, c.class_name))[:1]


def new_descriptors(root: ET.Element, package_name: str = PACKAGE) -> set[str]:
    winners = rank_launcher_candidates(collect_components(application_of(root), package_name))
    return {f"L{w.class_name.replace('.', '/')};" for w in winners}


# --------------------------------------------------------------------------------------
# The OLD algorithm, verbatim, for the regression half of every case
# --------------------------------------------------------------------------------------

def old_descriptors(root: ET.Element, package_name: str = PACKAGE) -> set[str]:
    application = application_of(root)

    def resolve(name: str) -> str:
        if not name.strip():
            return ""
        if name.startswith("."):
            return package_name + name
        if "." not in name:
            return f"{package_name}.{name}"
        return name

    def strict_name(element: ET.Element) -> str:
        # The old code read exactly one literal spelling.
        return element.get("android:name") or ""

    def is_launcher(component: ET.Element) -> bool:
        for flt in children(component, "intent-filter"):
            has_category = any(strict_name(c) == CATEGORY_LAUNCHER for c in children(flt, "category"))
            actions = children(flt, "action")
            has_main = (not actions) or any(strict_name(a) == ACTION_MAIN for a in actions)
            if has_category and has_main:
                return True
        return False

    names: list[str] = []
    for activity in children(application, "activity"):        # direct children only
        if is_launcher(activity):
            names.append(resolve(strict_name(activity)))
    for alias in children(application, "activity-alias"):     # bare targetActivity only
        if is_launcher(alias):
            names.append(resolve(alias.get("targetActivity") or ""))

    return {f"L{n.replace('.', '/')};" for n in names if n.strip()}


# --------------------------------------------------------------------------------------
# Manifest fixtures
# --------------------------------------------------------------------------------------

NSDECL = f'xmlns:android="{ANDROID_NS}"'
BODY_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<manifest {nsdecl} package="{package}">
    <uses-sdk android:minSdkVersion="26" />
    <application android:label="SwiftKey">{body}{ime}
    </application>
</manifest>
"""

IME_SERVICE = """
        <service android:name="com.touchtype.swiftkey.KeyboardService"
                 android:permission="android.permission.BIND_INPUT_METHOD"
                 android:exported="true">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
        </service>"""


def manifest_text(body: str, package: str = PACKAGE, nsdecl: str = NSDECL) -> str:
    return BODY_TEMPLATE.format(nsdecl=nsdecl, package=package, body=body, ime=IME_SERVICE)


def manifest(body: str, ns_aware: bool = False, package: str = PACKAGE, nsdecl: str = NSDECL) -> ET.Element:
    return parse_manifest(manifest_text(body, package, nsdecl), ns_aware=ns_aware)


LAUNCHER_FILTER = """
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>"""

# old_ok = the OLD algorithm already resolved this shape, so NEW must not change it.
CASES: list[dict] = [
    {
        "id": "baseline",
        "title": "Standard launcher activity - the shape that always worked",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": True,
        "xml": f"""
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true">{LAUNCHER_FILTER}
        </activity>""",
    },
    {
        "id": "dot_relative",
        "title": 'Dot-relative android:name (".MainActivity")',
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": True,
        "xml": f"""
        <activity android:name=".MainActivity" android:exported="true">{LAUNCHER_FILTER}
        </activity>""",
    },
    {
        "id": "extra_category",
        "title": "LAUNCHER accompanied by the INFO category",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": True,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.INFO" />
            </intent-filter>
        </activity>""",
    },
    {
        "id": "category_without_action",
        "title": "LAUNCHER category in a filter that declares no action at all",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": True,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true">
            <intent-filter>
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>""",
    },
    {
        "id": "alias_bare_target",
        "title": "activity-alias with a bare targetActivity (already worked)",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": True,
        "xml": f"""
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true" />
        <activity-alias android:name=".LauncherAlias"
                        targetActivity="com.touchtype.swiftkey.MainActivity"
                        android:exported="true">{LAUNCHER_FILTER}
        </activity-alias>""",
    },
    {
        "id": "split_filters",
        "title": "MAIN and LAUNCHER split across two separate intent-filters",
        "expect": "Lcom/touchtype/swiftkey/LauncherActivity;",
        # Already worked: the old check treated a filter with no <action> as MAIN, so the
        # LAUNCHER-only filter satisfied both halves. Kept to prove the rewrite preserved it.
        "old_ok": True,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.LauncherActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
            <intent-filter>
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>""",
    },
    {
        "id": "alias_prefixed_target",
        "title": "activity-alias declaring android:targetActivity (namespaced attribute)",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": False,
        "xml": f"""
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true" />
        <activity-alias android:name=".LauncherAlias"
                        android:targetActivity="com.touchtype.swiftkey.MainActivity"
                        android:exported="true">{LAUNCHER_FILTER}
        </activity-alias>""",
    },
    {
        "id": "custom_ns_prefix",
        "title": "Decoder that remapped the Android prefix (xmlns:a instead of xmlns:android)",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": False,
        # Both prefixes are bound, as a real manifest would: the component is written through the
        # remapped `a:` prefix while the surrounding document keeps `android:`.
        "nsdecl": f'xmlns:android="{ANDROID_NS}" xmlns:a="{ANDROID_NS}"',
        "xml": f"""
        <activity a:name="com.touchtype.swiftkey.MainActivity" a:exported="true">
            <intent-filter>
                <action a:name="android.intent.action.MAIN" />
                <category a:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>""",
    },
    {
        "id": "launcher_disabled",
        "title": "Launcher alias is enabled=false - the enabled target activity wins instead",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": False,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
        <activity-alias android:name=".LauncherAlias"
                        android:targetActivity="com.touchtype.swiftkey.MainActivity"
                        android:enabled="false" android:exported="true">""" + LAUNCHER_FILTER + """
        </activity-alias>""",
    },
    {
        "id": "main_only",
        "title": "MAIN action but no LAUNCHER category anywhere",
        "expect": "Lcom/touchtype/swiftkey/FlowPreferencesActivity;",
        "old_ok": False,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.FlowPreferencesActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>""",
    },
    {
        "id": "no_intent_filter",
        "title": "No intent-filter at all - graceful fallback still resolves one activity",
        "expect": "Lcom/touchtype/swiftkey/FlowPreferencesActivity;",
        "old_ok": False,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.FlowPreferencesActivity" android:exported="false" />""",
    },
    {
        "id": "tier3_prefers_alias",
        "title": "Tier 3 prefers an activity-alias over a plain activity",
        "expect": "Lcom/touchtype/swiftkey/MainActivity;",
        "old_ok": False,
        "xml": """
        <activity android:name="com.touchtype.swiftkey.NoiseActivity" android:exported="false" />
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true" />
        <activity-alias android:name=".Alias" targetActivity="com.touchtype.swiftkey.MainActivity" />""",
    },
]


# --------------------------------------------------------------------------------------
# Runner
# --------------------------------------------------------------------------------------

FAILURES: list[str] = []


def check(label: str, condition: bool, detail: str = "") -> None:
    if condition:
        print(f"    \u2713 {label}")
    else:
        print(f"    \u2717 {label} {detail}".rstrip())
        FAILURES.append(f"{label} {detail}".strip())


def run_cases() -> None:
    print("=" * 88)
    print("Launcher discovery across manifest shapes (namespace-unaware parser, as Morphe uses)")
    print("=" * 88)
    for case in CASES:
        print(f"\n[{case['id']}] {case['title']}")
        root = manifest(case["xml"], nsdecl=case.get("nsdecl", NSDECL))
        old = old_descriptors(root)
        new = new_descriptors(root)
        expected = {case["expect"]}

        check("NEW resolves the expected activity", new == expected,
              f"(got {sorted(new) or 'nothing'}, want {sorted(expected)})")
        if case["old_ok"]:
            check("OLD also resolved it - no regression on shapes that already worked",
                  old == expected, f"(got {sorted(old) or 'nothing'})")
        else:
            check("OLD failed here - this is the reported bug", not old,
                  f"(unexpectedly got {sorted(old)})")


def run_parser_modes() -> None:
    """The namespace-aware DOM must resolve through the getAttributeNS branch too."""
    print("\n" + "=" * 88)
    print("Parser modes")
    print("=" * 88)
    print("\n[ns-aware] Same fixtures re-parsed by a namespace-aware builder")
    broken = []
    for case in CASES:
        root = manifest(case["xml"], ns_aware=True, nsdecl=case.get("nsdecl", NSDECL))
        if new_descriptors(root) != {case["expect"]}:
            broken.append(f"{case['id']} -> {sorted(new_descriptors(root))}")
    check(f"all {len(CASES)} shapes still resolve under a namespace-aware DOM", not broken,
          f"(broken: {broken})")
    unaware_old = old_descriptors(manifest(CASES[0]["xml"]))
    aware_old = old_descriptors(manifest(CASES[0]["xml"], ns_aware=True))
    check("OLD only ever worked in the unaware mode it was written against",
          bool(unaware_old) and not aware_old,
          f"(unaware={sorted(unaware_old)} aware={sorted(aware_old)})")


def run_degradation() -> None:
    """The point of the fix: nothing resolves, yet nothing raises either."""
    print("\n" + "=" * 88)
    print("Graceful degradation")
    print("=" * 88)

    print("\n[empty] Manifest declaring no activity component at all")
    root = manifest("")
    check("no candidates are invented",
          rank_launcher_candidates(collect_components(application_of(root), PACKAGE)) == [])
    check("descriptor set is empty, so the hook is skipped and the run continues",
          new_descriptors(root) == set())

    print("\n[all-disabled] The only launcher is enabled=false")
    root = manifest(f"""
        <activity android:name="com.touchtype.swiftkey.MainActivity"
                  android:enabled="false" android:exported="true">{LAUNCHER_FILTER}
        </activity>""")
    check("a disabled launcher is not hooked", new_descriptors(root) == set(),
          f"(got {sorted(new_descriptors(root))})")

    print("\n[nameless] Launcher activity with no usable name attribute")
    root = manifest(f"""
        <activity android:exported="true">{LAUNCHER_FILTER}
        </activity>""")
    check("a nameless component yields nothing instead of a bogus descriptor",
          new_descriptors(root) == set(), f"(got {sorted(new_descriptors(root))})")

    print("\n[raises] No fixture may make discovery throw")
    try:
        for case in CASES:
            new_descriptors(manifest(case["xml"], nsdecl=case.get("nsdecl", NSDECL)))
        check("discovery never raises across every fixture", True)
    except Exception as exc:  # noqa: BLE001 - any escape is a failure
        check("discovery never raises across every fixture", False, f"(raised {exc!r})")


def run_tier_precedence() -> None:
    """A strict launcher must win even when looser candidates are also present."""
    print("\n" + "=" * 88)
    print("Tier precedence")
    print("=" * 88)
    print("\n[precedence] Strict launcher beats MAIN-only and bare activities")
    root = manifest("""
        <activity android:name="com.touchtype.swiftkey.NoiseActivity" android:exported="false" />
        <activity android:name="com.touchtype.swiftkey.MainOnlyActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
        <activity android:name="com.touchtype.swiftkey.MainActivity" android:exported="true">"""
                    + LAUNCHER_FILTER + """
        </activity>""")
    resolved = new_descriptors(root)
    check("exactly one candidate is returned", len(resolved) == 1, f"(got {sorted(resolved)})")
    check("it is the strict launcher, not a looser one",
          resolved == {"Lcom/touchtype/swiftkey/MainActivity;"}, f"(got {sorted(resolved)})")


def run_mirror_drift_guard() -> None:
    """Fail loudly if the Kotlin source stops matching what this script mirrors."""
    print("\n" + "=" * 88)
    print("Mirror drift guard (Kotlin source vs this mirror)")
    print("=" * 88)

    for path in (KOTLIN_PATCH, KOTLIN_HELPERS, SELF):
        check(f"{path.name} is present", path.is_file())
    if not (KOTLIN_PATCH.is_file() and KOTLIN_HELPERS.is_file() and SELF.is_file()):
        return

    patch_src = KOTLIN_PATCH.read_text(encoding="utf-8")
    helper_src = KOTLIN_HELPERS.read_text(encoding="utf-8")
    self_src = SELF.read_text(encoding="utf-8")

    kotlin_tiers = re.findall(r"// Tier (\d)", patch_src)
    python_tiers = re.findall(r"# Tier (\d)", self_src)
    check("Kotlin declares tiers 1-3", kotlin_tiers == ["1", "2", "3"], f"(found {kotlin_tiers})")
    check("mirror declares the same tiers", python_tiers == kotlin_tiers,
          f"(kotlin={kotlin_tiers} python={python_tiers})")

    for symbol in ("rankLauncherCandidates", "collectComponents", "resolveComponentName",
                   "describeComponents", "launcherActivityDescriptors", "LauncherCandidate"):
        check(f"Kotlin still defines {symbol}", symbol in patch_src)
    # Extension functions carry a receiver (`fun Element.children(`), so match by signature.
    for symbol in ("attr", "tagMatches", "children", "descendants",
                   "flagDefaultsTrue", "application", "metadata"):
        declared = re.search(rf"\bfun\s+(?:[\w.]+\s*\.\s*)?{re.escape(symbol)}\s*\(", helper_src)
        check(f"helpers still define {symbol}()", declared is not None)

    # The two behaviours that caused the reported outage must stay gone.
    check("manifest patch no longer throws when no launcher resolves",
          "no launcher activity found in the manifest" not in patch_src)
    check("bytecode patch no longer throws when the dex lacks the activity",
          "is missing from the dex" not in patch_src)
    check("both degrade with a WARNING instead", patch_src.count("WARNING:") == 2,
          f"(found {patch_src.count('WARNING:')})")

    # The discovery widenings this mirror relies on.
    check("discovery scans the subtree, not just direct children", "descendants(" in patch_src)
    check("attributes are matched by local name, not one literal prefix",
          "localPart(node.name) == localName" in helper_src)
    check("tags are matched by local name too", "localPart(element.tagName) == tag" in helper_src)
    check("aliases are read through the prefix-tolerant attr() helper",
          "attr(element, nameAttribute)" in patch_src)
    check("aliases retry targetActivity under both spellings",
          'read("activity-alias", "targetActivity")' in patch_src and
          'read("activity-alias", "name")' in patch_src)
    check("enabled=false components are filtered before the tiers",
          "it.className.isNotBlank() && it.enabled" in patch_src)
    check("the Patches screen is still declared when no hook target is found",
          "PatchesActivity" in patch_src and "application.metadata(FLAG_SETTINGS_UI" in patch_src)


def main() -> int:
    run_cases()
    run_parser_modes()
    run_degradation()
    run_tier_precedence()
    run_mirror_drift_guard()

    print("\n" + "=" * 88)
    if FAILURES:
        print(f"\u274c FAILED - {len(FAILURES)} assertion(s) across {len(CASES)} manifest shape(s):")
        for failure in FAILURES:
            print(f"   - {failure}")
        return 1
    print(f"\u2705 PASSED - {len(CASES)} manifest shapes x 2 parser modes, "
          f"plus degradation, precedence and drift guards")
    return 0


if __name__ == "__main__":
    sys.exit(main())
