package app.morphe.extension.swiftkey.voice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import app.morphe.extension.swiftkey.toolbar.KeyboardToolbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A real in-APK RecognitionService. PCM stays in memory and is never saved or uploaded. */
public final class OfflineRecognitionService extends RecognitionService {
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_SAMPLES = SAMPLE_RATE * 30;
    private static final int ERROR_LANGUAGE_NOT_SUPPORTED = 12;
    private static final int ERROR_LANGUAGE_UNAVAILABLE = 13;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Session active;
    private static WeakReference<OfflineRecognitionService> current = new WeakReference<>(null);

    @Override public void onCreate() {
        super.onCreate();
        current = new WeakReference<>(this);
    }

    /** Stops even a session started from SwiftKey's ORIGINAL mic button when the editor closes. */
    public static void cancelForKeyboard() {
        OfflineRecognitionService service = current.get();
        if (service == null) return;
        Session session = service.active;
        service.cancelActive();
        if (session != null) error(session.callback, SpeechRecognizer.ERROR_CLIENT);
    }

    @Override protected void onStartListening(Intent intent, Callback callback) {
        if (callback.getCallingUid() != Process.myUid() || !KeyboardToolbar.canRecord()) {
            error(callback, SpeechRecognizer.ERROR_CLIENT);
            return;
        }
        if (active != null) {
            error(callback, SpeechRecognizer.ERROR_RECOGNIZER_BUSY);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error(callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            return;
        }
        String requested = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        VoiceLanguage language = requested == null ? OfflineModelManager.selectedLanguage(this)
            : VoiceLanguage.fromTag(requested);
        if (language == null) {
            error(callback, ERROR_LANGUAGE_NOT_SUPPORTED);
            return;
        }
        if (!OfflineModelManager.isReady(this)) {
            KeyboardToolbar.showVoicePanel();
            error(callback, ERROR_LANGUAGE_UNAVAILABLE);
            return;
        }
        Session session = new Session(callback, language);
        active = session;
        worker.execute(() -> recognize(session));
    }

    @Override protected void onStopListening(Callback callback) {
        Session session = active;
        if (session != null) session.stop(false);
    }

    @Override protected void onCancel(Callback callback) {
        cancelActive();
    }

    @Override public void onDestroy() {
        cancelActive();
        worker.shutdown();
        if (current.get() == this) current.clear();
        super.onDestroy();
    }

    private void cancelActive() {
        Session session = active;
        active = null;
        if (session != null) session.stop(true);
    }

    private void recognize(Session session) {
        float[] samples = null;
        short[] buffer = null;
        int used = 0;
        boolean heardSpeech = false;
        try {
            samples = new float[MAX_SAMPLES];
            buffer = new short[2048];
            int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new AudioFailure();
            AudioRecord recorder;
            synchronized (session) {
                if (session.cancelled || session.finishRequested) {
                    deliver(session, c -> c.error(SpeechRecognizer.ERROR_NO_MATCH), true);
                    return;
                }
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 8192));
                session.recorder = recorder;
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new AudioFailure();
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new AudioFailure();
            }
            deliver(session, c -> c.readyForSpeech(new Bundle()), false);
            while (!session.finishRequested && !session.cancelled && used < MAX_SAMPLES) {
                int count = recorder.read(buffer, 0, Math.min(buffer.length, MAX_SAMPLES - used));
                if (count < 0) {
                    if (session.finishRequested || session.cancelled) break;
                    throw new AudioFailure();
                }
                if (count == 0) continue;
                double energy = 0;
                for (int i = 0; i < count; i++) {
                    samples[used++] = buffer[i] / 32768.0f;
                    energy += (double) buffer[i] * buffer[i];
                }
                if (!heardSpeech && energy / count > 150.0 * 150.0) {
                    heardSpeech = true;
                    deliver(session, Callback::beginningOfSpeech, false);
                }
            }
            releaseRecorder(session);
            if (session.cancelled) return;
            deliver(session, Callback::endOfSpeech, false);
            if (!heardSpeech || used < SAMPLE_RATE / 4) {
                deliver(session, c -> c.error(SpeechRecognizer.ERROR_NO_MATCH), true);
                return;
            }
            // Load/decode on the worker, only AFTER the microphone has been released.
            try (WhisperEngine engine = new WhisperEngine(OfflineModelManager.modelFile(this))) {
                session.engine = engine;
                if (session.cancelled) return;
                float[] clip = Arrays.copyOf(samples, used);
                String text;
                try { text = engine.transcribe(clip, session.language); }
                finally { Arrays.fill(clip, 0); }
                if (text.isEmpty()) {
                    deliver(session, c -> c.error(SpeechRecognizer.ERROR_NO_MATCH), true);
                } else {
                    Bundle results = new Bundle();
                    ArrayList<String> alternatives = new ArrayList<>();
                    alternatives.add(text);
                    results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, alternatives);
                    deliver(session, c -> c.results(results), true);
                }
            } finally {
                session.engine = null;
            }
        } catch (SecurityException e) {
            deliver(session, c -> c.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS), true);
        } catch (AudioFailure e) {
            deliver(session, c -> c.error(SpeechRecognizer.ERROR_AUDIO), true);
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            deliver(session, c -> c.error(SpeechRecognizer.ERROR_SERVER), true);
        } finally {
            if (samples != null) Arrays.fill(samples, 0);
            if (buffer != null) Arrays.fill(buffer, (short) 0);
            releaseRecorder(session);
        }
    }

    private static void releaseRecorder(Session session) {
        synchronized (session) {
            AudioRecord recorder = session.recorder;
            session.recorder = null;
            if (recorder != null) {
                try { recorder.stop(); } catch (IllegalStateException ignored) { }
                try { recorder.release(); } catch (RuntimeException ignored) { }
            }
        }
    }

    private interface Reply { void send(Callback callback) throws RemoteException; }

    private void deliver(Session session, Reply reply, boolean terminal) {
        main.post(() -> {
            if (active != session || session.cancelled) return;
            try { reply.send(session.callback); }
            catch (RemoteException ignored) {
                session.stop(true);
                if (active == session) active = null;
            } finally { if (terminal && active == session) active = null; }
        });
    }

    private static void error(Callback callback, int error) {
        try { callback.error(error); } catch (RemoteException ignored) { }
    }

    private static final class AudioFailure extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static final class Session {
        final Callback callback;
        final VoiceLanguage language;
        volatile boolean finishRequested;
        volatile boolean cancelled;
        volatile WhisperEngine engine;
        AudioRecord recorder;

        Session(Callback callback, VoiceLanguage language) {
            this.callback = callback;
            this.language = language;
        }

        void stop(boolean cancel) {
            finishRequested = true;
            if (cancel) cancelled = true;
            WhisperEngine current = engine;
            if (cancel && current != null) current.cancel();
            synchronized (this) {
                if (recorder != null) {
                    try { recorder.stop(); } catch (IllegalStateException ignored) { }
                }
            }
        }
    }
}
