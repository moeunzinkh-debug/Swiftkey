package app.morphe.extension.swiftkey.toolbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ToolbarViews {
    private ToolbarViews() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static boolean dark(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    static int background(Context context) { return Color.rgb(dark(context) ? 29 : 242, dark(context) ? 32 : 245, dark(context) ? 39 : 250); }
    static int foreground(Context context) { return dark(context) ? Color.WHITE : Color.rgb(28, 37, 50); }

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
        Context context = button.getContext();
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 10));
        shape.setColor(selected ? Color.rgb(32, 104, 205) : (dark(context) ? Color.rgb(47, 52, 63) : Color.WHITE));
        button.setBackground(shape);
        button.setTextColor(selected ? Color.WHITE : foreground(context));
        button.setSelected(selected);
    }
}
