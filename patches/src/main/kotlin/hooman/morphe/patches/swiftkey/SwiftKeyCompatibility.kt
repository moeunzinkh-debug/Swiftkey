package hooman.morphe.patches.swiftkey

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val swiftKeyCompatibility = Compatibility(
    name = "Microsoft SwiftKey",
    packageName = "com.touchtype.swiftkey",
    appIconColor = 0x00A4EF,
    // The new toolbar/native voice integration still needs device testing on these APK builds.
    // Fail-fast lifecycle/native-library checks are not a substitute for a real IME smoke test.
    targets = listOf(
        AppTarget("9.13.14.5", isExperimental = true, minSdk = 26),
        AppTarget("9.13.13.5", isExperimental = true, minSdk = 26),
    ),
)
