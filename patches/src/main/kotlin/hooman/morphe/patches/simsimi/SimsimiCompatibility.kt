package hooman.morphe.patches.simsimi

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val simsimiCompatibility = Compatibility(
    name = "SimSimi",
    packageName = "com.ismaker.android.simsimi",
    appIconColor = 0xFFD93A,
    targets = listOf(
        AppTarget("9.1.9"),
        AppTarget("9.1.8"),
        AppTarget("9.1.7"),
        AppTarget("9.1.2", isExperimental = true),
        AppTarget("8.7.5", isExperimental = true),
    ),
)
