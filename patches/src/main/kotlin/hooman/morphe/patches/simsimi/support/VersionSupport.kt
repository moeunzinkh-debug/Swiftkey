package hooman.morphe.patches.simsimi.support

import app.morphe.patcher.patch.PatchException

internal fun unsupportedSimsimiVersion(target: String, fingerprint: String) = PatchException(
    "SimSimi: $target changed in this APK build, so $fingerprint no longer matches. " +
        "These patches are verified for 9.1.9; other versions are experimental and may need re-deriving.",
)
