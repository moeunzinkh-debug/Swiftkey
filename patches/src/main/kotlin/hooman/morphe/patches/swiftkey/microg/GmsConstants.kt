package hooman.morphe.patches.swiftkey.microg

/**
 * បញ្ជី​ថេរ​ដែល​ប្រើ​ក្នុង​ការ​ប្ដូរ​ទិស GMS ទៅ GmsCore (ចម្លង​តាម​ការ​អនុវត្ត​ដែល
 * បាន​ផ្ទៀងផ្ទាត់​ហើយ​នៅក្នុង ReVanced/Morphe shared GmsCore support)។
 */
internal object GmsConstants {
    /**
     * សិទ្ធិ (permissions) ដែល GmsCore ប្រកាស​ក្រោម vendor app.revanced។
     * ប្តូរ​បាន​តែ​ឈ្មោះ​ដែល​ផ្គូផ្គង​ពិតប្រាកដ (exact match) ប៉ុណ្ណោះ — បើ​ឆ្គួត​ឆ្គង
     * នឹង​ប៉ះ​ឈ្មោះ​ថ្នាក់/កញ្ចប់​ឯកជន​របស់ Microsoft ដោយ​ចៃដន្យ។
     */
    val PERMISSIONS = setOf(
        "com.google.android.c2dm.permission.RECEIVE",
        "com.google.android.c2dm.permission.SEND",
        "com.google.android.gms.auth.api.phone.permission.SEND",
        "com.google.android.gms.permission.AD_ID",
        "com.google.android.gms.permission.AD_ID_NOTIFICATION",
        "com.google.android.googleapps.permission.GOOGLE_AUTH",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.cp",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.local",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.mail",
        "com.google.android.googleapps.permission.GOOGLE_AUTH.writely",
        "com.google.android.gtalkservice.permission.GTALK_SERVICE",
        "com.google.android.providers.gsf.permission.READ_GSERVICES",
    )

    /**
     * រាល់ service *action* ត្រូវ​ទុក​ជា​ឈ្មោះ​ដើម (GmsCore បម្រើ​វា​តាម​ឈ្មោះ​ពិត)
     * — កំណត់ត្រា​ស្រាវជ្រាវ​នៃ GmsCore-Patches បញ្ជាក់​ថា​ការ​ប្ដូរ action ធ្វើ​ឲ្យ
     * bind រក​សេវា​មិនឃើញ។
     */
    val ACTIONS: Set<String> = emptySet()

    /** Content provider authorities របស់ GMS ដែល GmsCore​ផ្តល់​ជំនួស។ */
    val AUTHORITIES = setOf(
        "com.google.android.gms.auth.accounts",
        "com.google.android.gms.chimera",
        "com.google.android.gms.fonts",
        "com.google.android.gms.phenotype",
        "com.google.android.gsf.gservices",
        "com.google.settings",
    )
}
