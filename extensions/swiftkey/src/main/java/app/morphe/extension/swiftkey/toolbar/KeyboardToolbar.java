package app.morphe.extension.swiftkey.toolbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.Region;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import app.morphe.extension.swiftkey.voice.OfflineRecognitionService;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Compact tools inside the IME input view, above (not replacing) SwiftKey's native keyboard.
 *
 * <p>The "Text editing" button prefers a slot INSIDE SwiftKey's own top toolbar row (the icon
 * strip), appended at the right end, so it sits where the user expects keyboard tools to be
 * instead of a separate button row on top of the keyboard. The wrapper itself then only hosts
 * the collapsible panels. If the native toolbar row cannot be identified within a few
 * layout attempts, it falls back to a dedicated button row above the keyboard.
 *
 * <p>The microphone affordance has two shapes: builds with the offline voice engine
 * (OFFLINE_VOICE) toggle the in-keyboard Offline mic panel, while builds with only the
 * microphone fix flag (PATCH_microphone_fix) open the Patches settings screen instead.
 */
public final class KeyboardToolbar extends LinearLayout {
    private static WeakReference<KeyboardToolbar> current = new WeakReference<>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Give the native-row search a short grace period, then fall back to the header row. */
    private static final int MAX_NATIVE_ATTEMPTS = 5;

    private final InputMethodService service;
    private final View keyboard;
    private final LinearLayout tools;
    private final boolean textEditing;
    private final boolean offlineVoice;
    private final boolean micUi;
    private TextEditingPanel editing;
    private OfflineVoicePanel voice;
    private Button editButton;
    private Button voiceButton;
    private Button micButton;
    private ImageButton nativeEditButton;
    private ImageButton nativeVoiceButton;
    private ImageButton nativeMicButton;
    private boolean nativeInstalled;
    private boolean fallbackInstalled;
    private int nativeAttempts;

    private KeyboardToolbar(InputMethodService service, View keyboard,
                            boolean textEditing, boolean offlineVoice, boolean micUi) {
        super(service);
        this.service = service;
        this.keyboard = keyboard;
        this.textEditing = textEditing;
        this.offlineVoice = offlineVoice;
        this.micUi = micUi;
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
            if (offlineVoice) {
                voice = new OfflineVoicePanel(service);
                voice.setVisibility(GONE);
                tools.addView(voice);
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
            boolean voice = settings != null && settings.getBoolean("app.morphe.swiftkey.OFFLINE_VOICE", false);
            boolean mic = settings != null && settings.getBoolean("app.morphe.swiftkey.PATCH_microphone_fix", false);
            if (!editing && !voice && !mic) return keyboard;
            KeyboardToolbar wrapper = new KeyboardToolbar(service, keyboard, editing, voice, mic);
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
                if (toolbar.nativeInstalled && (
                        (toolbar.nativeEditButton != null && !toolbar.nativeEditButton.isAttachedToWindow())
                            || (toolbar.nativeVoiceButton != null && !toolbar.nativeVoiceButton.isAttachedToWindow())
                            || (toolbar.nativeMicButton != null && !toolbar.nativeMicButton.isAttachedToWindow()))) {
                    // SwiftKey replaced the toolbar row with a new view tree; re-search it.
                    toolbar.nativeInstalled = false;
                    toolbar.nativeEditButton = null;
                    toolbar.nativeVoiceButton = null;
                    toolbar.nativeMicButton = null;
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

    /** Fail closed: an unreadable editor/keyboard state must never enable dictation. */
    public static boolean canRecord() {
        try {
            KeyboardToolbar toolbar = current.get();
            return toolbar != null && toolbar.voice != null && toolbar.isShown()
                && toolbar.service.isInputViewShown() && toolbar.service.getCurrentInputConnection() != null
                && !isSensitive(toolbar.service);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void showVoicePanel() {
        MAIN.post(() -> {
            try {
                KeyboardToolbar toolbar = current.get();
                if (toolbar == null || toolbar.voice == null) return;
                toolbar.reset();
                toolbar.voice.setVisibility(VISIBLE);
                toolbar.setVoiceSelected(true);
                toolbar.voice.refresh();
            } catch (Throwable ignored) {
            }
        });
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
        if (nativeInstalled || fallbackInstalled) return;
        if (!isAttachedToWindow()) return;
        scheduleNativeSearch(0);
    }

    private void scheduleNativeSearch(long delay) {
        if (nativeInstalled || fallbackInstalled) return;
        postDelayed(() -> runNativeSearch(), delay);
    }

    private void runNativeSearch() {
        if (nativeInstalled || fallbackInstalled || !isAttachedToWindow()) return;
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
        if (textEditing) {
            nativeEditButton = addNativeIconButton(row,
                ToolbarViews.textIcon(service, ToolbarViews.foreground(service)),
                "Show or hide text editing tools", v -> toggleEditing());
        }
        if (offlineVoice) {
            nativeVoiceButton = addNativeIconButton(row,
                ToolbarViews.micIcon(service, ToolbarViews.foreground(service)),
                "Show or hide offline voice typing", v -> toggleVoice());
        } else if (micUi) {
            nativeMicButton = addNativeIconButton(row,
                ToolbarViews.micIcon(service, ToolbarViews.foreground(service)),
                "Offline microphone settings", v -> openPatchesSettings());
            refreshMicState();
        }
    }

    private ImageButton addNativeIconButton(LinearLayout row, android.graphics.drawable.Drawable drawable,
                                            String description, View.OnClickListener listener) {
        ImageButton button = new ImageButton(service);
        button.setBackgroundResource(0);
        button.setImageDrawable(drawable);
        button.setContentDescription(description);
        int padding = ToolbarViews.dp(service, 12);
        button.setPadding(padding, padding, padding, padding);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(listener);
        row.addView(button, row.getChildCount());
        ToolbarViews.selectedNative(button, false);
        return button;
    }

    /** Fallback when the native toolbar row could not be identified. */
    private void installFallbackHeader() {
        if (fallbackInstalled || (!textEditing && !offlineVoice && !micUi)) return;
        fallbackInstalled = true;
        try {
            LinearLayout header = ToolbarViews.row(tools);
            View scroll = header.getParent() instanceof View ? (View) header.getParent() : (View) header;
            tools.removeView(scroll);
            // The panels (GONE) already occupy the first indices; the header must stay above them.
            tools.addView(scroll, 0);
            if (textEditing) {
                editButton = ToolbarViews.button(header, "Text editing", "Show or hide text editing tools",
                    v -> toggleEditing());
            }
            if (offlineVoice) {
                voiceButton = ToolbarViews.button(header, "Offline mic", "Show or hide offline voice typing",
                    v -> toggleVoice());
            } else if (micUi) {
                micButton = ToolbarViews.button(header, "Mic settings", "Offline microphone settings",
                    v -> openPatchesSettings());
            }
            refreshMicState();
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
            if (editing == null) return;
            boolean open = editing.getVisibility() != VISIBLE;
            reset();
            editing.setVisibility(open ? VISIBLE : GONE);
            setEditingSelected(open);
        } catch (Throwable ignored) {
        }
    }

    private void toggleVoice() {
        try {
            if (voice == null) return;
            boolean open = voice.getVisibility() != VISIBLE;
            reset();
            voice.setVisibility(open ? VISIBLE : GONE);
            setVoiceSelected(open);
            voice.refresh();
        } catch (Throwable ignored) {
        }
    }

    /** Opens the Patches settings screen (offline mic section); falls back to system settings. */
    private void openPatchesSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(service.getPackageName(),
                "app.morphe.extension.swiftkey.PatchesActivity"));
            intent.putExtra("app.morphe.swiftkey.SECTION", "mic");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(intent);
        } catch (Throwable ignored) {
            try {
                Intent intent = new Intent("android.settings.SPEECH_SERVICE_SETTINGS");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                service.startActivity(intent);
            } catch (Throwable ignored2) {
            }
        }
    }

    private boolean offlineMicEnabled() {
        try {
            Context app = service.getApplicationContext();
            return app.getSharedPreferences("app_morphe_swiftkey", Context.MODE_PRIVATE)
                .getBoolean("OFFLINE_MIC", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void refreshMicState() {
        try {
            boolean on = offlineMicEnabled();
            if (nativeMicButton != null) ToolbarViews.selectedNative(nativeMicButton, on);
            if (micButton != null) ToolbarViews.selected(micButton, on);
        } catch (Throwable ignored) {
        }
    }

    private void setEditingSelected(boolean selected) {
        if (editButton != null) ToolbarViews.selected(editButton, selected);
        if (nativeEditButton != null) ToolbarViews.selectedNative(nativeEditButton, selected);
    }

    private void setVoiceSelected(boolean selected) {
        if (voiceButton != null) ToolbarViews.selected(voiceButton, selected);
        if (nativeVoiceButton != null) ToolbarViews.selectedNative(nativeVoiceButton, selected);
    }

    private void reset() {
        try {
            if (editing != null) {
                editing.reset();
                editing.setVisibility(GONE);
            }
            if (voice != null) {
                voice.reset();
                voice.setVisibility(GONE);
            }
            setEditingSelected(false);
            setVoiceSelected(false);
            refreshMicState();
        } catch (Throwable ignored) {
        }
        try {
            OfflineRecognitionService.cancelForKeyboard();
        } catch (Throwable ignored) {
        }
    }
}
