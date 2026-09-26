package hooman.morphe.patches.instanttranslate.premium

/**
 * ការ​ស្គាល់​ឈ្មោះ​ដែល​ជា "ច្រក premium" — ប្រើ​ដោយ​បំណះ Unlock premium ទាំង​ពីរ​ជំហាន
 * (ហាម​ឲ្យ​មេតូដ boolean ត្រឡប់ true, និង​ហាម​ឲ្យ​ការ​អាន​field boolean ត្រឡប់ true)។
 *
 * ការ​ផ្គូផ្គង​ធ្វើ​តាម "ពាក្យ" មិនមែន​តាម substring ទេ ដូច្នេះ `isPro` ត្រូវ តែ `processData`
 * មិនត្រូវ — ការ​ផ្គូផ្គង​តាម substring សុទ្ធ​នឹង​បំផ្លាញ​មេតូដ​ធម្មតា​ដែល​មាន "pro"/"paid"
 * នៅ​ក្នុង​ឈ្មោះ។ ពាក្យ​ត្រូវ​បាន​កាត់​តាម `_`, `-`, `.`, `$` និង camelCase។
 *
 * បញ្ជី​ពាក្យ​នេះ​ស្រប​គ្នា​នឹង `extensions/instanttranslate/.../PremiumSupport.java` (ផ្នែក key
 * របស់ SharedPreferences) — បើ​បន្ថែម​ពាក្យ ត្រូវ​បន្ថែម​ទាំង​ពីរ​កន្លែង។
 */
internal object PremiumNames {

    private val SEPARATORS = Regex("[^A-Za-z0-9]+")

    /** `isPremium` → is | Premium, `PREMIUM_FLAG` → PREMIUM | FLAG. */
    private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

    /** ពាក្យ​ដែល​បញ្ជាក់​ថា​ជា​ច្រក premium / subscription។ */
    private val PREMIUM_WORDS = setOf(
        "premium", "pro", "vip", "member", "membership", "subscribed", "subscription",
        "entitled", "entitlement", "purchase", "purchased", "unlock", "unlocked",
        "fullversion", "paid", "noads", "adsfree", "adfree", "removeads", "adsremoved",
    )

    /**
     * ពាក្យ​វែង​គ្រប់គ្រាន់​ដើម្បី​ផ្គូផ្គង​ក្នុង​ឈ្មោះ​ដែល​សរសេរ​ជាប់​គ្នា (ឧ. `ispremium`,
     * `ismembership`). ពាក្យ​ខ្លី (pro/vip/paid) មិន​ចូល​រួម​ទេ ព្រោះ​វា​លេច​ក្នុង​ពាក្យ​ធម្មតា។
     */
    private val CONCATENATED_WORDS = PREMIUM_WORDS.filter { it.length >= 5 }.toSet()

    /**
     * ពាក្យ​ដែល​បដិសេធ​មេតូដ/field — ការ​បង្ខំ​ឲ្យ​ត្រឡប់ true នឹង​ធ្វើ​ឲ្យ​ខុស​អត្ថន័យ
     * (`isPremiumExpired`, `showPremiumBanner`) ឬ​នឹង​ប៉ះ​ទិន្នន័យ​ដែល​មិន​មែន​ជា​ស្ថានភាព
     * (`getPremiumPrice`, `premiumThemeId`)។
     */
    private val REJECT_WORDS = setOf(
        "expired", "invalid", "failed", "failure", "error", "denied", "blocked",
        "disabled", "hidden", "locked", "needed", "required", "should", "show", "shows",
        "visible", "prompt", "upsell", "banner", "dialog", "remind", "reminder",
        "price", "id", "index", "count", "date", "time", "timestamp",
        "url", "name", "text", "title",
    )

    /** បំបែក​ឈ្មោះ​មួយ​ទៅ​ជា​ពាក្យ​តូចៗ សម្រាប់​ការ​ផ្គូផ្គង​តាម​ពាក្យ។ */
    fun words(name: String): List<String> = SEPARATORS.split(name)
        .flatMap { part -> CAMEL_BOUNDARY.split(part) }
        .map { it.lowercase() }
        .filter { it.isNotEmpty() }

    /**
     * តើ​ឈ្មោះ [name] (method ឬ field) ជា​ច្រក​ premium ដែល​អាច​បង្ខំ​ឲ្យ​ជា​ពិត​បាន​ឬ​ទេ។
     */
    fun looksPremium(name: String): Boolean {
        val words = words(name)
        if (words.isEmpty()) return false
        if (words.any { it in REJECT_WORDS }) return false
        if (words.any { it in PREMIUM_WORDS }) return true
        // ឈ្មោះ​ដែល​សរសេរ​ជាប់​គ្នា​ទាំង​ស្រុង ដូចជា `ispremium` ឬ `is_fullversion`។
        val joined = words.joinToString("")
        return CONCATENATED_WORDS.any { joined.contains(it) }
    }
}
