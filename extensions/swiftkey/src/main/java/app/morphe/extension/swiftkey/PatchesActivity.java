package app.morphe.extension.swiftkey;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The "Patches" settings screen.
 *
 * <p>Declared in the target manifest by PatchesSettingsPatch and opened from the Patches row of
 * the SwiftKey settings screen and from the microphone icon in the keyboard toolbar. Shows:
 * <ul>
 *   <li>the patches actually applied to THIS build (manifest flags), and</li>
 *   <li>the offline microphone settings: engine status, offline mode toggle, engine switch and
 *       the offline engine installer (Kõnele).</li>
 * </ul>
 *
 * <p>Entirely programmatic (no resources) and defensive: this screen must never crash the app.
 */
@SuppressWarnings("unused")
public final class PatchesActivity extends Activity {

    public static final String PREFS = "app_morphe_swiftkey";
    public static final String KEY_OFFLINE_MIC = "OFFLINE_MIC";
    public static final String EXTRA_SECTION = "app.morphe.swiftkey.SECTION";
    public static final String SECTION_MIC = "mic";

    private static final String KONELE_URL = "https://github.com/Kaljurand/K6nele/releases";
    private static final String GOOGLE_SEARCH_APP = "com.google.android.googlequicksearchbox";
    private static final String GOOGLE_TTS_APP = "com.google.android.tts";

    private static final int BACKGROUND = 0xFF14171C;
    private static final int CARD = 0xFF1D2027;
    private static final int FOREGROUND = Color.WHITE;
    private static final int MUTED = 0xB3FFFFFF;

    private ScrollView scrollView;
    private View micSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(BACKGROUND);
        } catch (Throwable ignored) {
        }
        try {
            buildUi();
        } catch (Throwable t) {
            // Last resort: a plain message instead of a crash loop.
            setContentView(fallbackView("The Patches screen could not be built.\n" + t));
        }
    }

    private View fallbackView(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(FOREGROUND);
        view.setBackgroundColor(BACKGROUND);
        view.setPadding(dp(16), dp(24), dp(16), dp(16));
        return view;
    }

    private void buildUi() {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        // Header ---------------------------------------------------------------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(16), 0);
        header.setBackgroundColor(BACKGROUND);

        TextView back = new TextView(this);
        back.setText("\u2190");
        back.setTextColor(FOREGROUND);
        back.setTextSize(24);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(8), 0, dp(8), 0);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(this);
        title.setText("Patches");
        title.setTextColor(FOREGROUND);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        // Scrollable content ----------------------------------------------------
        scrollView = new ScrollView(this);
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scrollView.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- Section: applied patches ------------------------------------------
        content.addView(sectionTitle("Patches applied to this build"));
        String version = PatchesSettings.appVersion(this);
        List<String[]> applied = PatchesSettings.appliedPatches(this);
        content.addView(body(version + (version.isEmpty() ? "" : "  \u2022  ")
            + applied.size() + (applied.size() == 1 ? " patch applied" : " patches applied")));
        for (String[] entry : applied) {
            LinearLayout card = card();
            card.addView(body(entry[0], 15, FOREGROUND, Typeface.BOLD));
            card.addView(body(entry[1], 13, MUTED));
            content.addView(card, cardParams(4));
        }
        if (applied.isEmpty()) {
            content.addView(cardWith(body("No patch flags found on this build.", 14, MUTED)), cardParams(4));
        }

        // ---- Section: offline mic ----------------------------------------------
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, dp(20)));
        micSection = sectionTitle("Offline mic");
        content.addView(micSection);

        // Offline mode toggle
        final Switch toggle = new Switch(this);
        toggle.setTextColor(FOREGROUND);
        try {
            toggle.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_OFFLINE_MIC, false));
        } catch (Throwable ignored) {
        }
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_OFFLINE_MIC, isChecked).apply();
            } catch (Throwable ignored) {
            }
        });
        LinearLayout toggleRow = card();
        LinearLayout toggleTexts = new LinearLayout(this);
        toggleTexts.setOrientation(LinearLayout.VERTICAL);
        toggleTexts.addView(body("Offline mic mode", 15, FOREGROUND, Typeface.BOLD));
        toggleTexts.addView(body("Prefer a non-Google recognition engine for the keyboard mic.", 13, MUTED));
        LinearLayout.LayoutParams toggleTextsParams =
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        toggleRow.addView(toggleTexts, toggleTextsParams);
        toggleRow.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        content.addView(toggleRow, cardParams(4));

        // Current default engine
        content.addView(keyValueCard("Default engine", currentDefaultEngine()), cardParams(4));

        // Installed engines
        String engines = installedEngines();
        content.addView(keyValueCard("Installed engines", engines.isEmpty()
            ? "None found"
            : engines), cardParams(4));

        // Actions
        content.addView(actionRow("Change engine in system settings", v -> openEngineSettings()), cardParams(4));
        content.addView(actionRow("Get K\u00F5nele \u2014 offline speech engine", v -> openKonele()), cardParams(4));

        TextView note = body(
            "The microphone patch only removes the Google gate. The offline engine and its "
                + "models live in the engine app itself (e.g. K\u00F5nele downloads its models in "
                + "its own settings). Set the offline engine as the system default, and the "
                + "keyboard microphone works without Google.", 12, MUTED);
        LinearLayout.LayoutParams noteParams = cardParams(4);
        noteParams.topMargin = dp(12);
        content.addView(note, noteParams);

        setContentView(root);

        try {
            Intent launchIntent = getIntent();
            if (launchIntent != null && SECTION_MIC.equals(launchIntent.getStringExtra(EXTRA_SECTION))) {
                scrollToMicSection();
            }
        } catch (Throwable ignored) {
        }
    }

    private void scrollToMicSection() {
            scrollView.post(() -> {
                try {
                    int[] target = new int[2];
                    int[] scroll = new int[2];
                    micSection.getLocationOnScreen(target);
                    scrollView.getLocationOnScreen(scroll);
                    scrollView.smoothScrollTo(0, Math.max(0, target[1] - scroll[1]));
                } catch (Throwable ignored) {
                }
            });
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private String currentDefaultEngine() {
        try {
            String value = Settings.Global.getString(getContentResolver(), "recognition_service");
            if (value == null || value.isEmpty()) return "None";
            String pkg = value.substring(0, value.indexOf('/'));
            return isGoogle(pkg) ? pkg + "  (online)" : pkg + "  (offline-ready)";
        } catch (Throwable ignored) {
            return "Unknown";
        }
    }

    private String installedEngines() {
        List<String> names = new java.util.ArrayList<>();
        try {
            Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
            List<ResolveInfo> services = getPackageManager().queryIntentServices(intent, 0);
            if (services != null) {
                for (ResolveInfo info : services) {
                    if (info.serviceInfo != null && info.serviceInfo.packageName != null) {
                        String pkg = info.serviceInfo.packageName;
                        if (!names.contains(pkg)) names.add(pkg);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return String.join(", ", names);
    }

    private static boolean isGoogle(String pkg) {
        return GOOGLE_SEARCH_APP.equals(pkg) || GOOGLE_TTS_APP.equals(pkg);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void openEngineSettings() {
        // Try the most specific screen first; older Android versions only have the generic ones.
        String[] actions = {
            "android.settings.SPEECH_SERVICE_SETTINGS",
            "android.settings.RECOGNITION_SETTINGS_SCREEN",
            "android.settings.LANGUAGE_SETTINGS",
            Settings.ACTION_SETTINGS,
        };
        for (String action : actions) {
            try {
                Intent intent = new Intent(action);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, "Could not open system settings.", Toast.LENGTH_SHORT).show();
    }

    private void openKonele() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KONELE_URL)));
        } catch (Throwable t) {
            Toast.makeText(this, "Could not open the browser.", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // View helpers
    // ------------------------------------------------------------------

    private TextView sectionTitle(String text) {
        TextView view = body(text, 14, MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        params.bottomMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private TextView body(String text) {
        return body(text, 14, FOREGROUND);
    }

    private TextView body(String text, int size, int color) {
        return body(text, size, color, Typeface.NORMAL);
    }

    private TextView body(String text, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(CARD, 12));
        return card;
    }

    private LinearLayout cardWith(TextView... children) {
        LinearLayout card = card();
        for (TextView child : children) {
            card.addView(child, new LinearLayout.LayoutParams(-1, -2));
        }
        return card;
    }

    private LinearLayout keyValueCard(String key, String value) {
        LinearLayout card = card();
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(body(key, 15, FOREGROUND, Typeface.BOLD));
        texts.addView(body(value, 13, MUTED));
        card.addView(texts, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout actionRow(String text, View.OnClickListener listener) {
        LinearLayout card = card();
        TextView label = body(text, 15, 0xFF7FA8E8, Typeface.BOLD);
        label.setPadding(0, dp(4), 0, dp(4));
        card.addView(label, new LinearLayout.LayoutParams(-1, -2));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(listener);
        return card;
    }

    private LinearLayout.LayoutParams cardParams(int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        return params;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(radiusDp));
        shape.setColor(color);
        return shape;
    }

    private int dp(int value) {
        try {
            return Math.round(value * getResources().getDisplayMetrics().density);
        } catch (Throwable ignored) {
            return value * 2;
        }
    }
}
