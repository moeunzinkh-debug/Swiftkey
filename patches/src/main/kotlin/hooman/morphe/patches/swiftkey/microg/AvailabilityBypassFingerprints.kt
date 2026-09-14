package hooman.morphe.patches.swiftkey.microg

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * តួ​ខ្ទាស់​សម្រាប់​បិទ​ការ​ត្រួតពិនិត្យ "Google Play Services available"។
 *
 * យក​តាម​តួ​ដែល​បាន​ផ្ទៀងផ្ទាត់​ក្នុង shared GmsCore support (ReVanced/Morphe)៖
 * ទាំងពីរ​ស្ថិត​ក្នុង​បណ្ណាល័យ play-services-basement ដែល Firebase (Crashlytics/FCM)
 * របស់ SwiftKey​វេច​មក​ជាមួយ ហើយ​ឈ្មោះ/ស្នាម​ទាំងនេះ​មាន​លំនឹង​ឆ្លង​កំណែ។
 */
internal object GmsAvailabilityFingerprints {
    /**
     * GooglePlayServicesUtilLight.enforceNotGooglePlayServices(Context, int) —
     * បោះ​សារ "Google Play Services not available" ពេល​ពិនិត្យ​ឃើញ GMS បាត់/ចាស់។
     * បង្ខំ​ឲ្យ​មេតូដ​ត្រឡប់​ភ្លាម (return-void) ទើប​វា​មិន​បោះ។
     */
    val serviceCheck = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
        returnType = "V",
        parameters = listOf("L", "I"),
        strings = listOf("Google Play Services not available"),
    )

    /**
     * MetadataValueReader / isGooglePlayServicesAvailable(Context, int) តម្លៃ​លេខ
     * ("This should never happen.", "MetadataValueReader") — បង្ខំ​តម្លៃ​ត្រឡប់ 0
     * (ConnectionResult.SUCCESS)។
     */
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
