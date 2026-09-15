package hooman.morphe.patches.luckypatcher.support

import app.morphe.patcher.patch.PatchException

/**
 * សារកំហុសសម្រាប់ Lucky Patcher នៅពេល fingerprint មិនត្រូវ
 * ប៉ុន្តែយើងប្រើ soft-fail សម្រាប់ generic scanning ដូច្នេះកំហុសនេះកម្រនឹងកើត
 * លើកលែងតែ build ថ្មីប្លែកខ្លាំង។
 */
internal fun unsupportedLuckyPatcherVersion(target: String, fingerprint: String) = PatchException(
    "Lucky Patcher: $target changed in this APK build, so $fingerprint no longer matches. " +
        "These patches are designed to support all versions via generic scanning, " +
        "but this build's structure is significantly different. Please report the version.",
)
