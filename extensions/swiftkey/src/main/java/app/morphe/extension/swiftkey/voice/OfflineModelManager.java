package app.morphe.extension.swiftkey.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One shared multilingual model, stored privately without Android backup. No automatic downloads. */
public final class OfflineModelManager {
    public interface Listener { void onModelChanged(); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile boolean downloading;
    private static volatile boolean cancelled;
    private static volatile int progress;
    private static volatile String error;

    private OfflineModelManager() { }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("swiftkey_offline_voice", Context.MODE_PRIVATE);
    }

    public static VoiceLanguage selectedLanguage(Context context) {
        VoiceLanguage stored = VoiceLanguage.fromTag(preferences(context).getString("language", null));
        if (stored != null) return stored;
        VoiceLanguage system = VoiceLanguage.fromTag(Locale.getDefault().toLanguageTag());
        // This is only the initial, visible UI choice, not a recognition fallback.
        return system != null ? system : VoiceLanguage.ENGLISH;
    }

    public static void selectLanguage(Context context, VoiceLanguage language) {
        preferences(context).edit().putString("language", language.code).apply();
        notifyListeners();
    }

    public static File modelFile(Context context) {
        return new File(new File(context.getNoBackupFilesDir(), "offline-voice"), ModelDownload.FILE_NAME);
    }

    public static boolean isReady(Context context) {
        File model = modelFile(context);
        // Files arrive at this path only after SHA-256 validation and atomic installation.
        return model.isFile() && model.length() == ModelDownload.MODEL_BYTES;
    }

    public static boolean isDownloading() { return downloading; }
    public static int progress() { return progress; }
    public static String error() { return error; }

    public static void addListener(Listener listener) { LISTENERS.add(listener); }
    public static void removeListener(Listener listener) { LISTENERS.remove(listener); }

    public static synchronized void download(Context context) {
        if (downloading || isReady(context)) return;
        Context app = context.getApplicationContext();
        downloading = true;
        cancelled = false;
        progress = 0;
        error = null;
        notifyListeners();
        DOWNLOADS.execute(() -> {
            try {
                if (app.getNoBackupFilesDir().getUsableSpace() < ModelDownload.MODEL_BYTES + 16 * 1024 * 1024L) {
                    throw new IOException("Not enough storage for the 142 MiB voice model");
                }
                ModelDownload.download(modelFile(app), new ModelDownload.Progress() {
                    @Override public boolean isCancelled() { return cancelled; }
                    @Override public void update(long bytes) {
                        int next = (int) (bytes * 100 / ModelDownload.MODEL_BYTES);
                        if (next != progress) {
                            progress = next;
                            notifyListeners();
                        }
                    }
                });
            } catch (InterruptedIOException e) {
                error = cancelled ? "Download cancelled. Tap Download model to retry." : "Download timed out. Please retry.";
            } catch (IOException | RuntimeException e) {
                // Do not log download URLs, user speech, or editor contents.
                error = "Model download failed. Check your connection and storage, then retry.";
            } finally {
                downloading = false;
                notifyListeners();
            }
        });
    }

    public static void cancelDownload() { cancelled = true; }

    private static void notifyListeners() {
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) listener.onModelChanged();
        });
    }
}
