package app.morphe.extension.swiftkey.toolbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ToolbarViews {
    private ToolbarViews() { }

    static int dp(Context context, int value) {
        try {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        } catch (Throwable ignored) {
            return value * 2;
        }
    }

    static boolean dark(Context context) {
        try {
            return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static int background(Context context) {
        return Color.rgb(dark(context) ? 29 : 242, dark(context) ? 32 : 245, dark(context) ? 39 : 250);
    }

    static int foreground(Context context) {
        return dark(context) ? Color.WHITE : Color.rgb(28, 37, 50);
    }

    static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(foreground(context));
        view.setTextSize(13);
        view.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        return view;
    }

    static LinearLayout row(LinearLayout parent) {
        Context context = parent.getContext();
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2));
        scroll.addView(row);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    static Button button(LinearLayout row, String text, String description, View.OnClickListener listener) {
        Context context = row.getContext();
        Button button = new Button(context);
        button.setText(text);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setSingleLine(true);
        button.setMinWidth(dp(context, 48));
        button.setMinimumWidth(dp(context, 48));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, dp(context, 48));
        layout.setMargins(dp(context, 2), 0, dp(context, 2), 0);
        row.addView(button, layout);
        selected(button, false);
        return button;
    }

    static void selected(Button button, boolean selected) {
        if (button == null) return;
        Context context = button.getContext();
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 10));
        shape.setColor(selected ? Color.rgb(32, 104, 205) : (dark(context) ? Color.rgb(47, 52, 63) : Color.WHITE));
        button.setBackground(shape);
        button.setTextColor(selected ? Color.WHITE : foreground(context));
        button.setSelected(selected);
    }

    /** Selected state for a button living inside SwiftKey's native toolbar row. */
    static void selectedNative(View button, boolean selected) {
        if (button == null) return;
        Context context = button.getContext();
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 12));
        shape.setColor(selected ? Color.rgb(32, 104, 205) : Color.TRANSPARENT);
        button.setBackground(shape);
        button.setSelected(selected);
    }

    /** Monochrome microphone glyph matching the native toolbar icon style. */
    static Drawable micIcon(Context context, int color) {
        try {
            Bitmap bitmap = iconBitmap(context);
            float s = bitmap.getWidth() / 24f;
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            // Mic body (capsule), stem and base — filled.
            canvas.drawRoundRect(new RectF(9 * s, 3 * s, 15 * s, 13 * s), 3 * s, 3 * s, paint);
            canvas.drawRoundRect(new RectF(11 * s, 17 * s, 13 * s, 20 * s), 1 * s, 1 * s, paint);
            canvas.drawRoundRect(new RectF(8.5f * s, 19.5f * s, 15.5f * s, 21 * s), 1 * s, 1 * s, paint);
            // Cradle arc — stroked.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * s);
            canvas.drawArc(new RectF(5.5f * s, 4.5f * s, 18.5f * s, 17.5f * s), 0f, 180f, false, paint);
            return bitmapDrawable(context, bitmap);
        } catch (Throwable t) {
            return new GradientDrawable();
        }
    }

    /** Monochrome "text lines" glyph matching the native toolbar icon style. */
    static Drawable textIcon(Context context, int color) {
        try {
            Bitmap bitmap = iconBitmap(context);
            float s = bitmap.getWidth() / 24f;
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(3 * s, 5 * s, 21 * s, 8 * s), 1.5f * s, 1.5f * s, paint);
            canvas.drawRoundRect(new RectF(3 * s, 10.5f * s, 21 * s, 13.5f * s), 1.5f * s, 1.5f * s, paint);
            canvas.drawRoundRect(new RectF(3 * s, 16 * s, 14 * s, 19 * s), 1.5f * s, 1.5f * s, paint);
            return bitmapDrawable(context, bitmap);
        } catch (Throwable t) {
            return new GradientDrawable();
        }
    }

    /** 24dp icon canvas, scaled by density so the glyph stays crisp. */
    private static Bitmap iconBitmap(Context context) {
        float density = 2f;
        try {
            density = context.getResources().getDisplayMetrics().density;
        } catch (Throwable ignored) {
        }
        int size = Math.max(24, Math.round(24 * density));
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    }

    private static Drawable bitmapDrawable(Context context, Bitmap bitmap) {
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        return drawable;
    }
}
