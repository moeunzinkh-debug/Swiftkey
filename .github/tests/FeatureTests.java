import app.morphe.extension.swiftkey.toolbar.TextEditingActions;
import app.morphe.extension.swiftkey.toolbar.TextEditingActions.Action;
import app.morphe.extension.swiftkey.voice.ModelDownload;
import app.morphe.extension.swiftkey.voice.VoiceLanguage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/** No Android SDK, network, model weights, or test framework needed. Run with test-features.sh. */
public final class FeatureTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        languages();
        editing();
        modelInstallation();
        System.out.println("Feature tests passed (" + checks + " assertions).");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void languages() {
        check(VoiceLanguage.values().length == 4, "Only four language choices");
        check(VoiceLanguage.fromTag("km-KH") == VoiceLanguage.KHMER, "Khmer tag");
        check(VoiceLanguage.fromTag("EN_us") == VoiceLanguage.ENGLISH, "English tag normalization");
        check(VoiceLanguage.fromTag("th-TH") == VoiceLanguage.THAI, "Thai tag");
        check(VoiceLanguage.fromTag("zh-Hans-CN") == VoiceLanguage.CHINESE, "Chinese script tag");
        check(VoiceLanguage.fromTag("fr-FR") == null, "Unsupported languages must not silently become English");
        check(VoiceLanguage.fromTag(null) == null && VoiceLanguage.fromTag(" ") == null, "Missing language");
        check(!ModelDownload.URL.contains(".en.bin"), "Model must be multilingual, not English-only");
        check(ModelDownload.URL.contains("5359861c739e955e79d9a303bcbc70fb988958b1"), "Model revision is pinned");
        check(ModelDownload.SHA256.length() == 64 && ModelDownload.MODEL_BYTES == Long.parseLong("147951465"), "Pinned model integrity/size");
    }

    private static void editing() {
        FakeEditor editor = new FakeEditor();
        TextEditingActions actions = new TextEditingActions(editor);
        check(actions.perform(Action.LEFT) && !editor.extend, "Cursor without selecting");
        check(actions.perform(Action.SELECT) && actions.isSelecting(), "Enable selection");
        check(actions.perform(Action.RIGHT) && editor.extend, "Shift-selection navigation");
        check(actions.perform(Action.COPY) && !actions.isSelecting(), "Copy resets selection mode");
        for (Action action : new Action[] { Action.SELECT_ALL, Action.CUT, Action.PASTE, Action.UNDO, Action.REDO }) {
            check(actions.perform(action) && editor.last == action, "Forward native editor action " + action);
        }
        editor.supported = false;
        check(!actions.perform(Action.UNDO), "Do not pretend an unsupported undo succeeded");
        editor.supported = true;
        editor.sensitive = true;
        int calls = editor.contextCalls;
        check(!actions.perform(Action.CUT) && !actions.perform(Action.COPY), "Never export password text");
        check(editor.contextCalls == calls, "Blocked actions never reach the editor");
        check(actions.perform(Action.PASTE), "Pasting into a password is allowed");
        actions.perform(Action.SELECT);
        actions.reset();
        check(!actions.isSelecting(), "Input lifecycle resets selection");
        actions.perform(Action.SELECT);
        editor.available = false;
        check(!actions.perform(Action.LEFT) && !actions.isSelecting(), "Disconnected editor is safe");
    }

    private static void modelInstallation() throws Exception {
        File directory = Files.createTempDirectory("swiftkey-model-test-").toFile();
        File destination = new File(directory, "model.bin");
        byte[] good = "verified model fixture ខ្មែរ ไทย 中文".getBytes(StandardCharsets.UTF_8);
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(good));
        try {
            Progress progress = new Progress();
            ModelDownload.install(new ByteArrayInputStream(good), destination, good.length, hash, progress);
            check(Arrays.equals(Files.readAllBytes(destination.toPath()), good), "Verified model published");
            check(progress.bytes == good.length, "Download progress reported");
            check(!new File(directory, "model.bin.part").exists(), "Successful install leaves no partial file");
            expectFailure(destination, new byte[] { 1, 2 }, good.length, hash, new Progress(), good);
            expectFailure(destination, new byte[good.length + 1], good.length, hash, new Progress(), good);
            expectFailure(destination, good, good.length, new String(new char[64]).replace('\0', '0'), new Progress(), good);
            Progress cancelled = new Progress();
            cancelled.cancel = true;
            expectFailure(destination, good, good.length, hash, cancelled, good);
            Progress cancelDuringWrite = new Progress();
            cancelDuringWrite.cancelOnUpdate = true;
            expectFailure(destination, good, good.length, hash, cancelDuringWrite, good);
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(directory.toPath())) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (IOException e) { throw new RuntimeException(e); }
                });
            }
        }
    }

    private static void expectFailure(File destination, byte[] input, long size, String hash,
                                      Progress progress, byte[] original) throws Exception {
        try {
            ModelDownload.install(new ByteArrayInputStream(input), destination, size, hash, progress);
            throw new AssertionError("Invalid/cancelled model was accepted");
        } catch (IOException expected) {
            check(Arrays.equals(Files.readAllBytes(destination.toPath()), original), "Keep old model on failure/cancel");
            check(!new File(destination.getParentFile(), "model.bin.part").exists(), "Remove failed partial download");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    private static final class Progress implements ModelDownload.Progress {
        boolean cancel;
        boolean cancelOnUpdate;
        long bytes;
        @Override public boolean isCancelled() { return cancel; }
        @Override public void update(long count) { bytes = count; if (cancelOnUpdate) cancel = true; }
    }

    private static final class FakeEditor implements TextEditingActions.Editor {
        boolean available = true;
        boolean sensitive;
        boolean supported = true;
        boolean extend;
        int contextCalls;
        Action last;
        @Override public boolean available() { return available; }
        @Override public boolean sensitive() { return sensitive; }
        @Override public boolean navigate(Action action, boolean selection) { last = action; extend = selection; return supported; }
        @Override public boolean contextAction(Action action) { last = action; contextCalls++; return supported; }
    }
}
