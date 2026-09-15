package hooman.morphe.patches.simsimi.microg

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object GmsAvailabilityFingerprints {
    val serviceCheck = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
        returnType = "V",
        parameters = listOf("L", "I"),
        strings = listOf("Google Play Services not available"),
    )

    val googlePlayUtility = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
        returnType = "I",
        parameters = listOf("L", "I"),
        strings = listOf(
            "This should never happen.",
            "MetadataValueReader",
            "com.google.android.gms",
        ),
    )
}
