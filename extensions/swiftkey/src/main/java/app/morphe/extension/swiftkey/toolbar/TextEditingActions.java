package app.morphe.extension.swiftkey.toolbar;

/** Testable command state. The editor owns its undo history and clipboard; we store no text. */
public final class TextEditingActions {
    public enum Action { LEFT, UP, DOWN, RIGHT, HOME, END, SELECT, SELECT_ALL, CUT, COPY, PASTE, UNDO, REDO }

    public interface Editor {
        boolean available();
        boolean sensitive();
        boolean navigate(Action action, boolean extendSelection);
        boolean contextAction(Action action);
    }

    private final Editor editor;
    private boolean selecting;

    public TextEditingActions(Editor editor) { this.editor = editor; }
    public boolean isSelecting() { return selecting; }
    public void reset() { selecting = false; }

    public boolean perform(Action action) {
        if (!editor.available()) { reset(); return false; }
        if (action == Action.SELECT) { selecting = !selecting; return true; }
        switch (action) {
            case LEFT: case UP: case DOWN: case RIGHT: case HOME: case END:
                return editor.navigate(action, selecting);
            default:
                if (editor.sensitive() && (action == Action.COPY || action == Action.CUT)) return false;
                boolean performed = editor.contextAction(action);
                if (performed) reset();
                return performed;
        }
    }
}
