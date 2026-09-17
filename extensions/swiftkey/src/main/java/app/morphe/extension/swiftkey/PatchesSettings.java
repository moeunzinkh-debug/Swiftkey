package app.morphe.extension.swiftkey;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * "Patches" entry for the SwiftKey settings screen (the screen the app icon opens).
 *
 * <p>Every patch of this bundle stamps a manifest meta-data flag on the APK when it is
 * applied (see PatchesSettingsPatch and the resource patches of the other SwiftKey
 * patches). The entry reads those flags at runtime, so the dialog always shows the
 * patches that are actually present in THIS build, not the full catalog.
 *
 * <p>Everything is defensive: a failure here must never break the SwiftKey settings
 * screen, so all work is wrapped and swallowed.
 */
@SuppressWarnings("unused")
public final class PatchesSettings {

    private static final String ROW_TAG = "app.morphe.swiftkey.PATCHES_ROW";

    private static final String FLAG_DISABLE_TELEMETRY = "app.morphe.swiftkey.PATCH_disable_telemetry";
    private static final String FLAG_MICROPHONE_FIX = "app.morphe.swiftkey.PATCH_microphone_fix";
    private static final String FLAG_TEXT_EDITING = "app.morphe.swiftkey.PATCH_text_editing";
    private static final String FLAG_SETTINGS_UI = "app.morphe.swiftkey.PATCH_settings_ui";

    private PatchesSettings() {
    }

    /** Injected at the top of the launcher activity onCreate. Never throws. */
    public static void onActivityCreate(Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            decor.post(() -> {
                try {
                    install(activity);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void install(Activity activity) {
        ViewGroup content = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        if (content == null || content.findViewWithTag(ROW_TAG) != null) return;

        List<String[]> applied = collectApplied(readFlags(activity));
        View row = buildRow(activity, applied);
        if (row == null) return;

        // android.R.id.content is always a FrameLayout, so a bottom-anchored overlay row
        // is safe no matter how the settings screen is built internally (fragments, etc.).
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        content.addView(row, params);
        reserveScrollSpace(content, row);
    }

    private static Bundle readFlags(Activity activity) {
        try {
            ApplicationInfo info = activity.getPackageManager()
                .getApplicationInfo(activity.getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean flag(Bundle flags, String key) {
        if (flags == null) return false;
        try {
            return flags.getBoolean(key, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Patches applied to this build, in stable order. Used by both the row and the activity. */
    public static List<String[]> appliedPatches(Activity activity) {
        return collectApplied(readFlags(activity));
    }

    /** The target app version name (e.g. "9.13.13.5"), or "" when unknown. */
    public static String appVersion(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static List<String[]> collectApplied(Bundle flags) {
        List<String[]> applied = new ArrayList<>();
        if (flag(flags, FLAG_DISABLE_TELEMETRY)) {
            applied.add(new String[]{
                "Disable telemetry",
                "Stops SwiftKey telemetry, crash reporting and ad-attribution uploads.",
            });
        }
        if (flag(flags, FLAG_MICROPHONE_FIX)) {
            applied.add(new String[]{
                "Fix microphone voice input",
                "Microphone works without Google; any installed recognition engine is used.",
            });
        }
        if (flag(flags, FLAG_TEXT_EDITING)) {
            applied.add(new String[]{
                "Text editing toolbar",
                "Text editing tools in the keyboard toolbar: cursor, selection, cut/copy/paste, undo/redo.",
            });
        }
        if (flag(flags, FLAG_SETTINGS_UI)) {
            applied.add(new String[]{
                "Patches settings entry",
                "This Patches entry in the SwiftKey settings screen.",
            });
        }
        return applied;
    }

    private static View buildRow(final Activity activity, final List<String[]> applied) {
        boolean dark = isDark(activity);
        int foreground = dark ? Color.WHITE : Color.rgb(28, 37, 50);
        int muted = dark ? 0xB3FFFFFF : 0xB31C2532;

        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setTag(ROW_TAG);
        row.setBackgroundColor(dark ? Color.rgb(29, 32, 39) : Color.rgb(242, 245, 250));

        View divider = new View(activity);
        divider.setBackgroundColor(dark ? 0x33FFFFFF : 0x331C2532);
        row.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 1) / 16)));

        LinearLayout inner = new LinearLayout(activity);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        // Extra right padding keeps the chevron clear of the floating keyboard FAB.
        inner.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 72), dp(activity, 12));
        inner.setFocusable(true);
        inner.setClickable(true);

        View icon = new View(activity);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
        iconParams.setMarginEnd(dp(activity, 16));
        icon.setBackground(crossIcon(activity, foreground));
        inner.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Patches");
        title.setTextColor(foreground);
        title.setTextSize(17);
        title.setSingleLine(true);

        TextView subtitle = new TextView(activity);
        subtitle.setText(applied.size() == 1
            ? "1 patch applied to this build"
            : applied.size() + " patches applied to this build");
        subtitle.setTextColor(muted);
        subtitle.setTextSize(13);
        subtitle.setSingleLine(true);

        texts.addView(title, new LinearLayout.LayoutParams(-1, -2));
        texts.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inner.addView(texts, textsParams);

        TextView chevron = new TextView(activity);
        chevron.setText("\u203A");
        chevron.setTextColor(muted);
        chevron.setTextSize(22);
        chevron.setSingleLine(true);
        inner.addView(chevron, new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)));

        inner.setOnClickListener(v -> openPatchesScreen(activity, applied));

        row.addView(inner, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** Opens the Patches settings screen; falls back to the dialog when the activity is absent. */
    private static void openPatchesScreen(Activity activity, List<String[]> applied) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(activity.getPackageName(),
                "app.morphe.extension.swiftkey.PatchesActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable ignored) {
            showDialog(activity, applied);
        }
    }

    private static void showDialog(Activity activity, List<String[]> applied) {
        try {
            StringBuilder message = new StringBuilder();
            String appVersion = "";
            try {
                PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                appVersion = info.versionName == null ? "" : info.versionName;
            } catch (Throwable ignored) {
            }
            message.append("SwiftKey");
            if (!appVersion.isEmpty()) message.append(' ').append(appVersion);
            message.append('\n');
            message.append(applied.size()).append(" patch").append(applied.size() == 1 ? "" : "es")
                .append(" applied to this build\n\n");
            for (String[] entry : applied) {
                message.append("\u2022 ").append(entry[0]).append('\n');
                message.append("  ").append(entry[1]).append('\n');
            }
            new AlertDialog.Builder(activity)
                .setTitle("Patches")
                .setMessage(message.toString())
                .setCancelable(true)
                .setPositiveButton("OK", null)
                .show();
        } catch (Throwable ignored) {
        }
    }

    /**
     * The settings list scrolls; give it bottom padding so the last entry is not hidden
     * behind the overlay row. Best effort only, applied to the first scrollable child.
     */
    private static void reserveScrollSpace(ViewGroup content, View row) {
        try {
            if (content.getChildCount() == 0) return;
            View child = content.getChildAt(0);
            if (!(child instanceof ViewGroup)) return;
            final ViewGroup target = (ViewGroup) child;
            String name = target.getClass().getName();
            if (!(target instanceof android.widget.ScrollView)
                && !(target instanceof android.widget.HorizontalScrollView)
                && !name.contains("RecyclerView")
                && !name.contains("NestedScrollView")) {
                return;
            }
            row.post(() -> {
                try {
                    if (row.getHeight() <= 0) return;
                    target.setPadding(target.getPaddingLeft(), target.getPaddingTop(),
                        target.getPaddingRight(),
                        target.getPaddingBottom() + row.getHeight() + dp(target.getContext(), 8));
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Monochrome "medical cross" icon, matching the settings list icon style. */
    private static Drawable crossIcon(Context context, int color) {
        try {
            float density = 2f;
            try {
                density = context.getResources().getDisplayMetrics().density;
            } catch (Throwable ignored) {
            }
            int size = Math.max(24, Math.round(24 * density));
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            float s = size / 24f;
            Canvas canvas = new Canvas(bitmap);
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setColor(color);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(2 * s);
            canvas.drawCircle(12 * s, 12 * s, 9.5f * s, ringPaint);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(color);
            canvas.drawRoundRect(new RectF(10.6f * s, 6.8f * s, 13.4f * s, 17.2f * s), 1.4f * s, 1.4f * s, fill);
            canvas.drawRoundRect(new RectF(6.8f * s, 10.6f * s, 17.2f * s, 13.4f * s), 1.4f * s, 1.4f * s, fill);
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            drawable.setBounds(0, 0, size, size);
            return drawable;
        } catch (Throwable t) {
            return new GradientDrawable();
        }
    }

    private static int dp(Context context, int value) {
        try {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        } catch (Throwable ignored) {
            return value * 2;
        }
    }

    private static boolean isDark(Context context) {
        try {
            return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
