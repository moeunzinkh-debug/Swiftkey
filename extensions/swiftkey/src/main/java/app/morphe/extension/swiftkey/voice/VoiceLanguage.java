package app.morphe.extension.swiftkey.voice;

import java.util.Locale;

/** The UI deliberately exposes only these four languages. Never silently switch languages. */
public enum VoiceLanguage {
    KHMER("km", "ខ្មែរ"),
    ENGLISH("en", "English"),
    THAI("th", "ไทย"),
    CHINESE("zh", "中文");

    public final String code;
    public final String label;

    VoiceLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static VoiceLanguage fromTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return null;
        String language = tag.trim().replace('_', '-').split("-", 2)[0].toLowerCase(Locale.ROOT);
        for (VoiceLanguage value : values()) {
            if (value.code.equals(language)) return value;
        }
        return null;
    }
}
