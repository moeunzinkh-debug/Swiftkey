package app.morphe.extension.swiftkey.toolbar;

import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.Region;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Compact tools inside the IME input view, above (not replacing) SwiftKey's native keyboard.
 *
 * <p>The "Text editing" button prefers a slot INSIDE SwiftKey's own top toolbar row (the icon
 * strip), appended at the right end, so it sits where the user expects keyboard tools to be
 * instead of a separate button row on top of the keyboard. The wrapper itself then only hosts
 * the collapsible editing panel. If the native toolbar row cannot be identified within a few
 * layout attempts, it falls back to a dedicated button row above the keyboard.
 */
public final class KeyboardToolbar extends LinearLayout {
    private static WeakReference<KeyboardToolbar> current = new WeakReference<>(null);

    /** Give the native-row search a short grace period, then fall back to the header row. */
    private static final int MAX_NATIVE_ATTEMPTS = 5;

    private final InputMethodService service;
    private final View keyboard;
    private final LinearLayout tools;
    private final boolean textEditing;
    private TextEditingPanel editing;
    private Button editButton;
    private ImageButton nativeEditButton;
    private boolean nativeInstalled;
    private boolean fallbackInstalled;
    private int nativeAttempts;

    private KeyboardToolbar(InputMethodService service, View keyboard, boolean textEditing) {
        super(service);
        this.service = service;
        this.keyboard = keyboard;
        this.textEditing = textEditing;
        setOrientation(VERTICAL);
        try {
            ViewGroup.LayoutParams original = keyboard.getLayoutParams();
            if (original != null) {
                ViewGroup.LayoutParams outer = original instanceof FrameLayout.LayoutParams
                    ? new FrameLayout.LayoutParams((FrameLayout.LayoutParams) original)
                    : original instanceof ViewGroup.MarginLayoutParams
                        ? new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) original)
                        : new ViewGroup.LayoutParams(original);
                // A fixed old keyboard height must not clip the extra toolbar/panels.
                outer.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                setLayoutParams(outer);
            }
            tools = new LinearLayout(service);
            tools.setOrientation(VERTICAL);
            tools.setBackgroundColor(ToolbarViews.background(service));
            addView(tools, new LayoutParams(-1, -2));
            if (textEditing) {
                editing = new TextEditingPanel(service);
                editing.setVisibility(GONE);
                tools.addView(editing);
            }
            // MATCH_PARENT on an inner input view would consume the toolbar's space as well.
            int height = original != null && original.height >= 0 ? original.height : ViewGroup.LayoutParams.WRAP_CONTENT;
            int width = original != null && original.width >= 0 ? original.width : ViewGroup.LayoutParams.MATCH_PARENT;
            addView(keyboard, new LayoutParams(width, height));
        } catch (Throwable t) {
            throw new RuntimeException("KeyboardToolbar init failed", t);
        }
    }

    public static View wrap(InputMethodService service, View keyboard) {
        if (keyboard == null) return null;
        if (keyboard instanceof KeyboardToolbar) {
            current = new WeakReference<>((KeyboardToolbar) keyboard);
            return keyboard;
        }
        // SwiftKey may return its cached ORIGINAL child on later onCreateInputView calls.
        // Return the existing wrapper, not a child still attached to that wrapper.
        if (keyboard.getParent() instanceof KeyboardToolbar) {
            KeyboardToolbar owner = (KeyboardToolbar) keyboard.getParent();
            if (owner.service == service) {
                current = new WeakReference<>(owner);
                return owner;
            }
        }
        if (keyboard.getParent() != null) return keyboard;
        try {
            Bundle settings = service.getPackageManager().getApplicationInfo(service.getPackageName(),
                PackageManager.GET_META_DATA).metaData;
            boolean editing = settings != null && settings.getBoolean("app.morphe.swiftkey.TEXT_EDITING", false);
            if (!editing) return keyboard;
            KeyboardToolbar wrapper = new KeyboardToolbar(service, keyboard, editing);
            current = new WeakReference<>(wrapper);
            return wrapper;
        } catch (Throwable t) {
            // A tools UI failure must never prevent the original keyboard from opening.
            try {
                if (keyboard.getParent() instanceof ViewGroup) {
                    ((ViewGroup) keyboard.getParent()).removeView(keyboard);
                }
            } catch (Throwable ignored) {
            }
            return keyboard;
        }
    }

    public static void resetForInput(InputMethodService service) {
        try {
            KeyboardToolbar toolbar = current.get();
            if (toolbar != null && toolbar.service == service) {
                toolbar.reset();
                if (toolbar.nativeInstalled && toolbar.nativeEditButton != null
                    && !toolbar.nativeEditButton.isAttachedToWindow()) {
                    // SwiftKey replaced the toolbar row with a new view tree; re-search it.
                    toolbar.nativeInstalled = false;
                    toolbar.nativeEditButton = null;
                    toolbar.nativeAttempts = 0;
                }
                // The input view may have been swapped; resume the native-row search if needed.
                toolbar.kickoffNativeSearch();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void includeToolsInInsets(InputMethodService service, InputMethodService.Insets insets) {
        try {
            KeyboardToolbar toolbar = current.get();
            if (toolbar == null || toolbar.service != service || !toolbar.isShown()) return;
            int[] location = new int[2];
            toolbar.tools.getLocationInWindow(location);
            Rect bounds = new Rect(location[0], location[1], location[0] + toolbar.tools.getWidth(),
                location[1] + toolbar.tools.getHeight());
            insets.contentTopInsets = Math.min(insets.contentTopInsets, bounds.top);
            insets.visibleTopInsets = Math.min(insets.visibleTopInsets, bounds.top);
            if (insets.touchableInsets == InputMethodService.Insets.TOUCHABLE_INSETS_REGION) {
                insets.touchableRegion.op(bounds, Region.Op.UNION);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isSensitive(InputMethodService service) {
        try {
            EditorInfo editor = service.getCurrentInputEditorInfo();
            if (editor == null) return true;
            int type = editor.inputType & InputType.TYPE_MASK_CLASS;
            int variation = editor.inputType & InputType.TYPE_MASK_VARIATION;
            return type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        kickoffNativeSearch();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) reset();
    }

    @Override
    protected void onDetachedFromWindow() {
        reset();
        if (current.get() == this) current.clear();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------
    // Native toolbar row integration
    // ------------------------------------------------------------------

    private void kickoffNativeSearch() {
        if (!textEditing || editing == null || nativeInstalled || fallbackInstalled) return;
        if (!isAttachedToWindow()) return;
        scheduleNativeSearch(0);
    }

    private void scheduleNativeSearch(long delay) {
        if (nativeInstalled || fallbackInstalled) return;
        postDelayed(() -> runNativeSearch(), delay);
    }

    private void runNativeSearch() {
        if (nativeInstalled || fallbackInstalled || editing == null || !isAttachedToWindow()) return;
        if (keyboard.isLaidOut() && keyboard.getHeight() > 0) {
            LinearLayout row = findNativeToolbarRow(keyboard);
            if (row != null) {
                try {
                    installNativeButton(row);
                    nativeInstalled = true;
                    return;
                } catch (Throwable t) {
                    // Keep trying; the fallback header is the last resort.
                }
            }
        }
        nativeAttempts++;
        if (nativeAttempts >= MAX_NATIVE_ATTEMPTS) {
            installFallbackHeader();
            return;
        }
        scheduleNativeSearch(nativeAttempts < 3 ? 150 : 400);
    }

    private void installNativeButton(LinearLayout row) {
        ImageButton button = new ImageButton(service);
        button.setBackgroundResource(0);
        button.setImageDrawable(ToolbarViews.textIcon(service, ToolbarViews.foreground(service)));
        button.setContentDescription("Show or hide text editing tools");
        int padding = ToolbarViews.dp(service, 12);
        button.setPadding(padding, padding, padding, padding);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(v -> toggleEditing());
        row.addView(button, row.getChildCount());
        nativeEditButton = button;
        ToolbarViews.selectedNative(button, false);
    }

    /** Fallback when the native toolbar row could not be identified. */
    private void installFallbackHeader() {
        if (fallbackInstalled || editing == null) return;
        fallbackInstalled = true;
        try {
            LinearLayout header = ToolbarViews.row(tools);
            View scroll = header.getParent() instanceof View ? (View) header.getParent() : (View) header;
            tools.removeView(scroll);
            // The panel (GONE) already occupies index 0; the header must stay above it.
            tools.addView(scroll, 0);
            editButton = ToolbarViews.button(header, "Text editing", "Show or hide text editing tools",
                v -> toggleEditing());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Finds SwiftKey's native toolbar row: the shallowest horizontal icon-button strip near the
     * top of the input view (the layers/emoji/clipboard/... strip). Returns null when nothing
     * matches; the caller then falls back to its own header row.
     */
    static LinearLayout findNativeToolbarRow(View root) {
        try {
            if (!(root instanceof ViewGroup) || root.getHeight() <= 0) return null;
            ViewGroup tree = (ViewGroup) root;
            int topLimit = tree.getHeight() / 4;
            int minRow = ToolbarViews.dp(tree.getContext(), 36);
            int maxRow = ToolbarViews.dp(tree.getContext(), 100);
            int[] rootLocation = new int[2];
            tree.getLocationInWindow(rootLocation);

            // Breadth-first so the shallowest (topmost) matching row wins.
            Deque<View> queue = new ArrayDeque<>();
            queue.add(tree);
            int visited = 0;
            while (!queue.isEmpty() && visited < 500) {
                View view = queue.poll();
                visited++;
                if (view instanceof LinearLayout
                    && isToolbarRow((LinearLayout) view, rootLocation, topLimit, minRow, maxRow)) {
                    return (LinearLayout) view;
                }
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = group.getChildCount() - 1; i >= 0; i--) {
                        queue.add(group.getChildAt(i));
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isToolbarRow(LinearLayout candidate, int[] rootLocation,
                                        int topLimit, int minRow, int maxRow) {
        if (candidate.getOrientation() != LinearLayout.HORIZONTAL) return false;
        int count = candidate.getChildCount();
        if (count < 4) return false;
        int height = candidate.getHeight();
        if (height < minRow || height > maxRow) return false;
        int[] location = new int[2];
        candidate.getLocationInWindow(location);
        int relativeTop = location[1] - rootLocation[1];
        if (relativeTop < 0 || relativeTop > topLimit) return false;

        int clickable = 0;
        int icons = 0;
        for (int i = 0; i < count; i++) {
            View child = candidate.getChildAt(i);
            if (child == null) continue;
            if (child.isClickable()) clickable++;
            if (containsImage(child, 0)) icons++;
        }
        return clickable * 5 >= count * 3 && icons * 2 >= count;
    }

    private static boolean containsImage(View view, int depth) {
        if (view instanceof ImageView) return true;
        if (depth >= 3 || !(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null && containsImage(child, depth + 1)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Shared behavior
    // ------------------------------------------------------------------

    private void toggleEditing() {
        try {
            boolean open = editing.getVisibility() != VISIBLE;
            reset();
            editing.setVisibility(open ? VISIBLE : GONE);
            setEditingSelected(open);
        } catch (Throwable ignored) {
        }
    }

    private void setEditingSelected(boolean selected) {
        if (editButton != null) ToolbarViews.selected(editButton, selected);
        if (nativeEditButton != null) ToolbarViews.selectedNative(nativeEditButton, selected);
    }

    private void reset() {
        try {
            if (editing != null) {
                editing.reset();
                editing.setVisibility(GONE);
            }
            setEditingSelected(false);
        } catch (Throwable ignored) {
        }
    }
}
