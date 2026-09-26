package app.morphe.extension.instanttranslate;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ជំនួយ​សម្រាប់​បំណះ "Unlock premium" របស់ Instant Translate On Screen។
 *
 * <p>រាល់​ការ​ហៅ {@code Context.getSharedPreferences(...)} នៅ​ក្នុង dex របស់​កម្មវិធី
 * ត្រូវ​បាន​ប្ដូរ​ទៅ {@link #getSharedPreferences(Context, String, int)} ដែល​រុំ
 * SharedPreferences ពិត​ទុក​ក្នុង {@link UnlockedPreferences}។ ការ​រុំ​នេះ​មិន​កែ​ទិន្នន័យ​ពិត
 * ទេ (ការ​សរសេរ​ទៅ​ដើម​ដដែល) — វា​គ្រាន់​តែ​រាយការណ៍​តម្លៃ​របស់ key ដែល​ឈ្មោះ​ជា​ច្រក
 * premium ថា "បាន​បើក" នៅ​ពេល​កម្មវិធី​អាន។
 *
 * <p>បញ្ជី​ពាក្យ​នៅ​ទីនេះ (សម្រាប់ key) ស្រប​គ្នា​នឹង
 * {@code hooman.morphe.patches.instanttranslate.premium.PremiumNames} (សម្រាប់ ឈ្មោះ method/field)
 * — បើ​បន្ថែម​ពាក្យ ត្រូវ​បន្ថែម​ទាំង​ពីរ​កន្លែង។
 *
 * <p>គ្រប់​មេតូដ​មិន​បោះ exception ចេញ​ទេ ហើយ​បើ​មាន​បញ្ហា វា​ត្រឡប់​តម្លៃ​ដើម​វិញ —
 * ការ​រុំ prefs មិន​អាច​ធ្វើ​ឲ្យ​កម្មវិធី​គាំង​បាន​ឡើយ។
 */
@SuppressWarnings("unused")
public final class PremiumSupport {

    private PremiumSupport() {
    }

    /** ពាក្យ​ដែល​បញ្ជាក់​ថា key ជា​ច្រក premium / subscription។ */
    private static final String[] PREMIUM_WORDS = {
        "premium", "pro", "vip", "member", "membership", "subscribed", "subscription",
        "entitled", "entitlement", "purchase", "purchased", "unlock", "unlocked",
        "fullversion", "paid", "noads", "adsfree", "adfree", "removeads", "adsremoved",
    };

    /**
     * ពាក្យ​វែង​គ្រប់គ្រាន់​សម្រាប់​ផ្គូផ្គង​ក្នុង​ key ដែល​សរសេរ​ជាប់​គ្នា (ឧ. {@code ispremium})។
     * ពាក្យ​ខ្លី (pro/vip/paid) មិន​ចូល​រួម​ទេ ព្រោះ​វា​លេច​ក្នុង​ពាក្យ​ធម្មតា។
     */
    private static final String[] CONCATENATED_WORDS = {
        "premium", "member", "membership", "subscribed", "subscription", "entitled",
        "entitlement", "purchase", "purchased", "unlock", "unlocked", "fullversion",
        "noads", "adsfree", "adfree", "removeads", "adsremoved",
    };

    /**
     * ពាក្យ​ដែល​បដិសេធ key — ការ​រាយការណ៍​ថា​បាន​បើក​នឹង​ធ្វើ​ឲ្យ​ខុស​អត្ថន័យ
     * ({@code is_premium_expired}) ឬ​បំផ្លាញ​ទិន្នន័យ​ដែល​មិន​មែន​ជា​ស្ថានភាព
     * ({@code premium_theme_id}, JSON, token…)。
     */
    private static final String[] REJECT_WORDS = {
        "expired", "invalid", "failed", "failure", "error", "denied", "blocked",
        "disabled", "hidden", "locked", "needed", "required", "price", "currency",
        "cost", "id", "index", "count", "version", "url", "name", "text", "title",
        "message", "description", "language", "locale", "theme", "color", "style",
        "json", "data", "config", "token", "signature", "hash",
    };

    /** ពាក្យ​ដែល​បង្ហាញ​ថា key ជា​កាលបរិច្ឆេទ/រយៈពេល → ត្រូវ​រាយការណ៍​ជា​តម្លៃ​អតិបរមា។ */
    private static final String[] TIME_WORDS = {
        "expire", "expiry", "expiration", "until", "end", "date", "time",
        "timestamp", "millis", "seconds", "deadline",
    };

    /**
     * ពាក្យ​ចុងក្រោយ​ដែល​អនុញ្ញាត​ឲ្យ​រាយការណ៍ key ប្រភេទ String ថា "true"។ ចំណាំ៖ String
     * មិន​ត្រូវ​បាន​រាយការណ៍​ជា​ទូទៅ​ទេ ព្រោះ​ key ជា JSON/សារ​អាច​ខូច​បើ​កែ​តម្លៃ។
     */
    private static final String[] STRING_KEY_WORDS = {
        "premium", "pro", "vip", "subscribed", "purchased", "unlocked", "paid",
        "status", "state", "type",
    };

    /**
     * ជំនួស {@code Context.getSharedPreferences}។ ត្រឡប់ SharedPreferences ដែល​រុំ​ដើម​ទុក។
     *
     * @return SharedPreferences ដើម​វិញ បើ​ការ​រុំ​មិន​អាច​ធ្វើ​បាន
     */
    public static SharedPreferences getSharedPreferences(Context context, String name, int mode) {
        // ការ​ហៅ​នេះ​ជា​ការ​ហៅ Context ពិត (កូដ extension មិន​ត្រូវ​បាន​កែ​ដោយ​បំណះ) — ដូច្នេះ
        // គ្មាន​រង្វិល​ជុំ​ទេ។ បើ Context បោះ exception ដូច​ដើម វា​នឹង​ចេញ​ទៅ​អ្នក​ហៅ​ដូច​ដើម​ដែរ។
        final SharedPreferences delegate = context.getSharedPreferences(name, mode);
        if (delegate == null || delegate instanceof UnlockedPreferences) return delegate;
        try {
            return new UnlockedPreferences(delegate);
        } catch (Throwable t) {
            return delegate;
        }
    }

    /** តើ key នេះ​ជា​ច្រក premium ដែល​គួរ​រាយការណ៍​ថា​បាន​បើក​ឬ​ទេ។ */
    static boolean isPremiumKey(String key) {
        if (key == null || key.isEmpty()) return false;
        final String[] words = words(key);
        for (String word : words) {
            if (has(REJECT_WORDS, word)) return false;
        }
        for (String word : words) {
            if (has(PREMIUM_WORDS, word)) return true;
        }
        final String joined = join(words);
        for (String token : CONCATENATED_WORDS) {
            if (joined.contains(token)) return true;
        }
        return false;
    }

    /** តើ key នេះ​ជា​កាលបរិច្ឆេទ / រយៈពេល (ត្រូវ​រាយការណ៍​ជា​តម្លៃ​អតិបរមា) ឬ​ទេ។ */
    private static boolean isTimeKey(String key) {
        for (String word : words(key)) {
            if (has(TIME_WORDS, word)) return true;
        }
        return false;
    }

    /** តើ key ប្រភេទ String គួរ​រាយការណ៍​ជា "true" ឬ​ទេ (ពាក្យ​ចុងក្រោយ​ត្រូវ​ជា​ពាក្យ​រដ្ឋ)។ */
    private static boolean isStatusLikeStringKey(String key) {
        final String[] words = words(key);
        if (words.length == 0) return false;
        return has(STRING_KEY_WORDS, words[words.length - 1]);
    }

    /** បំបែក​ឈ្មោះ​មួយ​ទៅ​ជា​ពាក្យ​តូចៗ (ដូច PremiumNames.words ខាង Kotlin)។ */
    private static String[] words(String name) {
        final List<String> words = new ArrayList<>();
        for (String part : name.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            for (String piece : part.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                if (!piece.isEmpty()) words.add(piece.toLowerCase(Locale.ROOT));
            }
        }
        return words.toArray(new String[0]);
    }

    private static String join(String[] words) {
        final StringBuilder builder = new StringBuilder();
        for (String word : words) builder.append(word);
        return builder.toString();
    }

    private static boolean has(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) return true;
        }
        return false;
    }

    /**
     * SharedPreferences ដែល​រាយការណ៍ key premium ថា​បាន​បើក ហើយ​បញ្ជូន​អ្វី​ផ្សេង​ទៀត​ទៅ​ដើម។
     */
    private static final class UnlockedPreferences implements SharedPreferences {
        private final SharedPreferences delegate;

        UnlockedPreferences(SharedPreferences delegate) {
            this.delegate = delegate;
        }

        @Override
        public Map<String, ?> getAll() {
            final Map<String, ?> all = delegate.getAll();
            if (all == null || all.isEmpty()) return all;
            Map<String, Object> copy = null;
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                final String key = entry.getKey();
                if (!isPremiumKey(key)) continue;
                final Object forced = forcedValue(key, entry.getValue());
                final Object current = entry.getValue();
                if (forced == null || forced.equals(current)) continue;
                if (copy == null) {
                    copy = new HashMap<>();
                    copy.putAll(all);
                }
                copy.put(key, forced);
            }
            if (copy == null) return all;
            return copy;
        }

        /** តម្លៃ​ដែល​នឹង​រាយការណ៍​ជំនួស — null មានន័យ​ថា​រក្សា​តម្លៃ​ដើម។ */
        private static Object forcedValue(String key, Object value) {
            if (value instanceof Boolean) return Boolean.TRUE;
            if (value instanceof Integer) {
                return isTimeKey(key) ? Integer.valueOf(Integer.MAX_VALUE) : Integer.valueOf(1);
            }
            if (value instanceof Long) {
                return isTimeKey(key) ? Long.valueOf(Long.MAX_VALUE) : Long.valueOf(1L);
            }
            return null;
        }

        @Override
        public String getString(String key, String defValue) {
            if (isPremiumKey(key) && isStatusLikeStringKey(key)) return "true";
            return delegate.getString(key, defValue);
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return delegate.getStringSet(key, defValues);
        }

        @Override
        public int getInt(String key, int defValue) {
            if (!isPremiumKey(key)) return delegate.getInt(key, defValue);
            return isTimeKey(key) ? Integer.MAX_VALUE : 1;
        }

        @Override
        public long getLong(String key, long defValue) {
            if (!isPremiumKey(key)) return delegate.getLong(key, defValue);
            return isTimeKey(key) ? Long.MAX_VALUE : 1L;
        }

        @Override
        public float getFloat(String key, float defValue) {
            if (!isPremiumKey(key)) return delegate.getFloat(key, defValue);
            return isTimeKey(key) ? Float.MAX_VALUE : 1f;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return isPremiumKey(key) || delegate.getBoolean(key, defValue);
        }

        @Override
        public boolean contains(String key) {
            return isPremiumKey(key) || delegate.contains(key);
        }

        @Override
        public Editor edit() {
            return delegate.edit();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
            delegate.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
            delegate.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}
