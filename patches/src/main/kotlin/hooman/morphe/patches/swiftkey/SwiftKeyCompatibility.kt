package hooman.morphe.patches.swiftkey

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val swiftKeyCompatibility = Compatibility(
    name = "Microsoft SwiftKey",
    packageName = "com.touchtype.swiftkey",
    appIconColor = 0x00A4EF,
    // ប្រកាសពីកំណែថ្មីទៅចាស់ (តាមឯកសាររបស់ Morphe patcher)។
    // 9.13.14.5 ដាក់ជា experimental ព្រោះ fingerprint ទាំងឡាយបានផ្ទៀងផ្ទាត់តែលើ 9.13.13.5៖
    // បំណះ "Disable cloud sign-in prompt" និង "Disable telemetry" នៅតែបរាជ័យលើ 9.13.14.5
    // រហូតដល់ fingerprint ត្រូវបានទាញយកឡើងវិញពី APK 9.13.14.5។
    targets = listOf(
        AppTarget("9.13.14.5", isExperimental = true),
        AppTarget("9.13.13.5"),
    ),
)
