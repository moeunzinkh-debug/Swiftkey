package app.morphe.extension.swiftkey.toolbar;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import app.morphe.extension.swiftkey.voice.MicrophonePermissionActivity;
import app.morphe.extension.swiftkey.voice.OfflineModelManager;
import app.morphe.extension.swiftkey.voice.OfflineRecognitionService;
import app.morphe.extension.swiftkey.voice.VoiceLanguage;

import java.util.ArrayList;
import java.util.EnumMap;

final class OfflineVoicePanel extends LinearLayout implements OfflineModelManager.Listener {
    private final InputMethodService service;
    private final EnumMap<VoiceLanguage, Button> languages = new EnumMap<>(VoiceLanguage.class);
    private final TextView status;
    private final ProgressBar progress;
    private final Button download;
    private final Button record;
    private final Button cancel;
    private SpeechRecognizer recognizer;
    private int generation;
    private boolean stopping;
    private String message;

    OfflineVoicePanel(InputMethodService service) {
        super(service);
        this.service = service;
        setOrientation(VERTICAL);
        LinearLayout languageRow = ToolbarViews.row(this);
        for (VoiceLanguage language : VoiceLanguage.values()) {
            languages.put(language, ToolbarViews.button(languageRow, language.label,
                "Voice language: " + language.label, view -> {
                    discard();
                    message = null;
                    OfflineModelManager.selectLanguage(service, language);
                    refresh();
                }));
        }
        status = ToolbarViews.label(service, "");
        status.setMaxLines(3);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(status);
        progress = new ProgressBar(service, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        addView(progress, new LayoutParams(-1, ToolbarViews.dp(service, 4)));
        LinearLayout controls = ToolbarViews.row(this);
        download = ToolbarViews.button(controls, "Download model", "Download the shared 142 MiB offline model", view -> {
            message = null;
            OfflineModelManager.download(service);
            refresh();
        });
        record = ToolbarViews.button(controls, "Start", "Start or stop offline dictation", view -> {
            if (recognizer == null) start();
            else if (!stopping) {
                stopping = true;
                message = "Finishing recording…";
                recognizer.stopListening();
                refresh();
            }
        });
        cancel = ToolbarViews.button(controls, "Cancel", "Cancel download or discard dictation", view -> {
            if (recognizer != null) discard();
            else OfflineModelManager.cancelDownload();
            message = "Cancelled. Nothing was inserted.";
            refresh();
        });
        refresh();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        OfflineModelManager.addListener(this);
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        OfflineModelManager.removeListener(this);
        discard();
        super.onDetachedFromWindow();
    }

    @Override public void onModelChanged() { refresh(); }

    void refresh() {
        VoiceLanguage selected = OfflineModelManager.selectedLanguage(service);
        for (VoiceLanguage language : languages.keySet()) {
            ToolbarViews.selected(languages.get(language), language == selected);
        }
        boolean ready = OfflineModelManager.isReady(service);
        boolean downloading = OfflineModelManager.isDownloading();
        progress.setVisibility(downloading ? VISIBLE : GONE);
        progress.setProgress(OfflineModelManager.progress());
        download.setVisibility(ready ? GONE : VISIBLE);
        download.setEnabled(!downloading);
        record.setEnabled(ready && !stopping);
        record.setAlpha(record.isEnabled() ? 1.0f : 0.45f);
        download.setAlpha(download.isEnabled() ? 1.0f : 0.45f);
        record.setText(recognizer == null ? "Start" : stopping ? "Working…" : "Stop");
        cancel.setVisibility(downloading || recognizer != null ? VISIBLE : GONE);
        String text;
        if (downloading) text = "Downloading shared model · " + OfflineModelManager.progress() + "% of 142 MiB";
        else if (message != null) text = message;
        else if (OfflineModelManager.error() != null) text = OfflineModelManager.error();
        else if (ready) text = selected.label + " · Model ready · Offline dictation · 30 seconds maximum";
        else text = "Download once (142 MiB) for ខ្មែរ, English, ไทย and 中文. Internet is used only for this download.";
        status.setText(text);
    }

    private void start() {
        if (!KeyboardToolbar.canRecord()) {
            message = "Open a text field. Voice input is disabled in password fields.";
            refresh();
            return;
        }
        if (!OfflineModelManager.isReady(service)) return;
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            MicrophonePermissionActivity.request(service);
            return;
        }
        final InputConnection target = service.getCurrentInputConnection();
        final EditorInfo editor = service.getCurrentInputEditorInfo();
        final int session = ++generation;
        stopping = false;
        message = "Preparing microphone…";
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(service,
                new ComponentName(service, OfflineRecognitionService.class));
            recognizer.setRecognitionListener(new RecognitionListener() {
                private boolean current() { return session == generation && recognizer != null; }
                @Override public void onReadyForSpeech(Bundle params) {
                    if (!current()) return;
                    message = "Listening offline · Tap Stop when finished (30 seconds maximum)";
                    refresh();
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float value) { }
                @Override public void onBufferReceived(byte[] data) { }
                @Override public void onPartialResults(Bundle results) { }
                @Override public void onEvent(int event, Bundle params) { }
                @Override public void onEndOfSpeech() {
                    if (!current()) return;
                    stopping = true;
                    message = "Transcribing on this device…";
                    refresh();
                }
                @Override public void onError(int error) {
                    if (!current()) return;
                    discard();
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            message = "No speech recognized. Try again in a quiet place."; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            message = "Allow microphone permission in SwiftKey app settings."; break;
                        case SpeechRecognizer.ERROR_AUDIO:
                            message = "Microphone unavailable. Close other recording apps and retry."; break;
                        case 12: case 13:
                            message = "Select one of the four languages and download the model first."; break;
                        default:
                            message = "Offline voice failed. Check the model and device compatibility, then retry.";
                    }
                    refresh();
                }
                @Override public void onResults(Bundle results) {
                    if (!current()) return;
                    ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = texts == null || texts.isEmpty() ? "" : texts.get(0);
                    // Never insert delayed speech into another app/field, or into a password.
                    if (target != null && target == service.getCurrentInputConnection()
                            && editor == service.getCurrentInputEditorInfo() && KeyboardToolbar.canRecord()) {
                        try {
                            target.finishComposingText();
                            message = !text.isEmpty() && target.commitText(text, 1)
                                ? "Text inserted · Offline" : "This editor did not accept the text.";
                        } catch (RuntimeException ignored) {
                            message = "The editor is no longer available. Dictation discarded.";
                        }
                    } else message = "The text field changed. Dictation discarded.";
                    discard();
                    refresh();
                }
            });
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, OfflineModelManager.selectedLanguage(service).code)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizer.startListening(request);
        } catch (RuntimeException e) {
            discard();
            message = "Could not start offline voice. Check that the microphone patch is installed.";
        }
        refresh();
    }

    void discard() {
        generation++;
        SpeechRecognizer old = recognizer;
        recognizer = null;
        stopping = false;
        if (old != null) {
            try { old.cancel(); } catch (RuntimeException ignored) { }
            try { old.destroy(); } catch (RuntimeException ignored) { }
        }
    }

    void reset() {
        discard();
        message = null;
        refresh();
    }
}
