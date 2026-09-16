package app.morphe.extension.swiftkey.toolbar;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import app.morphe.extension.swiftkey.toolbar.TextEditingActions.Action;

final class TextEditingPanel extends LinearLayout {
    private final TextEditingActions actions;
    private Button select;

    TextEditingPanel(InputMethodService service) {
        super(service);
        setOrientation(VERTICAL);
        actions = new TextEditingActions(new TextEditingActions.Editor() {
            @Override public boolean available() {
                try {
                    return service.getCurrentInputConnection() != null;
                } catch (Throwable ignored) {
                    return false;
                }
            }
            @Override public boolean sensitive() {
                try {
                    return KeyboardToolbar.isSensitive(service);
                } catch (Throwable ignored) {
                    return true;
                }
            }
            @Override public boolean navigate(Action action, boolean extend) {
                try {
                    InputConnection editor = service.getCurrentInputConnection();
                    if (editor == null) return false;
                    int key;
                    switch (action) {
                        case LEFT: key = KeyEvent.KEYCODE_DPAD_LEFT; break;
                        case RIGHT: key = KeyEvent.KEYCODE_DPAD_RIGHT; break;
                        case UP: key = KeyEvent.KEYCODE_DPAD_UP; break;
                        case DOWN: key = KeyEvent.KEYCODE_DPAD_DOWN; break;
                        case HOME: key = KeyEvent.KEYCODE_MOVE_HOME; break;
                        case END: key = KeyEvent.KEYCODE_MOVE_END; break;
                        default: return false;
                    }
                    int meta = extend ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0;
                    long time = SystemClock.uptimeMillis();
                    editor.finishComposingText();
                    boolean down = editor.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0,
                        meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD));
                    boolean up = editor.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0,
                        meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD));
                    return down && up;
                } catch (Throwable ignored) {
                    return false;
                }
            }
            @Override public boolean contextAction(Action action) {
                try {
                    InputConnection editor = service.getCurrentInputConnection();
                    if (editor == null) return false;
                    int id;
                    switch (action) {
                        case SELECT_ALL: id = android.R.id.selectAll; break;
                        case CUT: id = android.R.id.cut; break;
                        case COPY: id = android.R.id.copy; break;
                        case PASTE: id = android.R.id.paste; break;
                        case UNDO: id = android.R.id.undo; break;
                        case REDO: id = android.R.id.redo; break;
                        default: return false;
                    }
                    editor.finishComposingText();
                    return editor.performContextMenuAction(id);
                } catch (Throwable ignored) {
                    return false;
                }
            }
        });
        LinearLayout navigation = ToolbarViews.row(this);
        add(navigation, "←", "Move cursor left", Action.LEFT);
        add(navigation, "↑", "Move cursor up", Action.UP);
        add(navigation, "↓", "Move cursor down", Action.DOWN);
        add(navigation, "→", "Move cursor right", Action.RIGHT);
        add(navigation, "Home", "Start of line", Action.HOME);
        add(navigation, "End", "End of line", Action.END);
        LinearLayout commands = ToolbarViews.row(this);
        select = add(commands, "Select", "Toggle text selection", Action.SELECT);
        add(commands, "Select all", "Select all text", Action.SELECT_ALL);
        add(commands, "Cut", "Cut selected text", Action.CUT);
        add(commands, "Copy", "Copy selected text", Action.COPY);
        add(commands, "Paste", "Paste clipboard", Action.PASTE);
        add(commands, "Undo", "Undo in the current editor", Action.UNDO);
        add(commands, "Redo", "Redo in the current editor", Action.REDO);
    }

    private Button add(LinearLayout row, String label, String description, Action action) {
        return ToolbarViews.button(row, label, description, view -> {
            try {
                if (!actions.perform(action)) {
                    Toast.makeText(getContext(), "This editor does not allow that action.", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) {
                actions.reset();
            }
            ToolbarViews.selected(select, actions.isSelecting());
        });
    }

    void reset() {
        try {
            actions.reset();
            ToolbarViews.selected(select, false);
        } catch (Throwable ignored) {
        }
    }
}
