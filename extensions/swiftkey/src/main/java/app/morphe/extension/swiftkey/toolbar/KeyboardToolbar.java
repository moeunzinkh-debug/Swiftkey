package app.morphe.extension.swiftkey.toolbar;

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
import android.widget.LinearLayout;

import app.morphe.extension.swiftkey.voice.OfflineRecognitionService;

import java.lang.ref.WeakReference;

/** Compact tools inside the IME input view, above (not replacing) SwiftKey's native keyboard. */
public final class KeyboardToolbar extends LinearLayout {
    private static WeakReference<KeyboardToolbar> current = new WeakReference<>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final InputMethodService service;
    private final LinearLayout tools;
    private TextEditingPanel editing;
    private OfflineVoicePanel voice;
    private Button editButton;
    private Button voiceButton;

    private KeyboardToolbar(InputMethodService service, View keyboard, boolean textEditing, boolean microphone) {
        super(service);
        this.service = service;
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
            LinearLayout header = ToolbarViews.row(tools);
            if (textEditing) {
                editing = new TextEditingPanel(service);
                editing.setVisibility(GONE);
                editButton = ToolbarViews.button(header, "Text editing", "Show or hide text editing tools", view -> {
                    boolean open = editing.getVisibility() != VISIBLE;
                    reset();
                    editing.setVisibility(open ? VISIBLE : GONE);
                    ToolbarViews.selected(editButton, open);
                });
                tools.addView(editing);
            }
            if (microphone) {
                voice = new OfflineVoicePanel(service);
                voice.setVisibility(GONE);
                voiceButton = ToolbarViews.button(header, "Offline mic", "Show or hide offline voice typing", view -> {
                    boolean open = voice.getVisibility() != VISIBLE;
                    reset();
                    voice.setVisibility(open ? VISIBLE : GONE);
                    ToolbarViews.selected(voiceButton, open);
                    voice.refresh();
                });
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
            if (!editing && !voice) return keyboard;
            KeyboardToolbar wrapper = new KeyboardToolbar(service, keyboard, editing, voice);
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
            if (toolbar != null && toolbar.service == service) toolbar.reset();
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
                ToolbarViews.selected(toolbar.voiceButton, true);
                toolbar.voice.refresh();
            } catch (Throwable ignored) {
            }
        });
    }

    private void reset() {
        try {
            if (editing != null) { editing.reset(); editing.setVisibility(GONE); }
            if (voice != null) { voice.reset(); voice.setVisibility(GONE); }
            if (editButton != null) ToolbarViews.selected(editButton, false);
            if (voiceButton != null) ToolbarViews.selected(voiceButton, false);
        } catch (Throwable ignored) {
        }
        try {
            OfflineRecognitionService.cancelForKeyboard();
        } catch (Throwable ignored) {
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) reset();
    }

    @Override protected void onDetachedFromWindow() {
        reset();
        if (current.get() == this) current.clear();
        super.onDetachedFromWindow();
    }
}
