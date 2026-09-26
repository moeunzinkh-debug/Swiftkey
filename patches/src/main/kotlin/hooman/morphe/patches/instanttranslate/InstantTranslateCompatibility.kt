package hooman.morphe.patches.instanttranslate

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * "Instant Translate On Screen" (Sapiens Labs) — screen translation tool with a premium /
 * subscription tier (auto-region translate, full-screen and AI translation, offline packs).
 *
 * កំណែ​គាំទ្រ​ត្រូវ​បាន​ប្រកាស​ជា "any version" (version = null) ព្រោះ​បំណះ unlock នេះ​ស្វែងរក
 * ច្រក​ premium ដោយ​ខ្លួនឯង (heuristic) ហើយ​រាយការណ៍​នៅ​ពេល patch — មិន​ចាំបាច់​ចាក់សោ
 * តាម version ជាក់លាក់​ទេ។ បើ APK ជាកំណែ​ដែល​រក​មិន​ឃើញ​ច្រក​ណា​មួយ បំណះ​នឹង​បរាជ័យ​ជាមួយ
 * សារ​ពន្យល់ (មិន​បង្កើត APK ដែល​គិត​ថា​បាន unlock ទេ)។
 */
internal val instantTranslateCompatibility = Compatibility(
    name = "Instant Translate On Screen",
    packageName = "com.spaceship.screen.textcopy",
    appIconColor = 0x2E7DF7,
    targets = listOf(
        AppTarget(
            version = null,
            isExperimental = true,
            description = "Any build of the app; the unlock patch reports what it found in its log.",
        ),
    ),
)
