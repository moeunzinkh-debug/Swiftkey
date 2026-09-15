package app.morphe.extension.swiftkey.voice;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** JNI bridge to the CPU-only whisper.cpp library shipped inside the patched APK. */
public final class WhisperEngine implements AutoCloseable {
    static { System.loadLibrary("swiftkey_whisper"); }

    private long handle;

    public WhisperEngine(File model) throws IOException {
        handle = nativeCreate(model.getAbsolutePath());
        if (handle == 0) throw new IOException("Could not load the offline voice model");
    }

    /** Called only on the recognition worker. close() must run on that same worker. */
    public String transcribe(float[] samples, VoiceLanguage language) throws IOException {
        long current;
        synchronized (this) { current = handle; }
        if (current == 0) throw new IOException("Voice engine is closed");
        byte[] result = nativeTranscribe(current, samples, language.code);
        if (result == null) throw new IOException("Offline transcription failed or was cancelled");
        // JNI modified UTF-8 would mishandle supplementary characters; decode real UTF-8 in Java.
        return new String(result, StandardCharsets.UTF_8).trim();
    }

    public synchronized void cancel() {
        if (handle != 0) nativeCancel(handle);
    }

    @Override public synchronized void close() {
        if (handle != 0) {
            nativeFree(handle);
            handle = 0;
        }
    }

    private static native long nativeCreate(String modelPath);
    private static native byte[] nativeTranscribe(long handle, float[] samples, String language);
    private static native void nativeCancel(long handle);
    private static native void nativeFree(long handle);
}
