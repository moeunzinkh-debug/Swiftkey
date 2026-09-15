package app.morphe.extension.swiftkey;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * អ្នកគ្រប់គ្រង Vosk Offline Speech Recognition Model សម្រាប់ SwiftKey។
 *
 * <p>គាំទ្រ ៤ ភាសាគោល៖
 * <ul>
 *   <li><b>ភាសាខ្មែរ (Khmer - km):</b> Lightweight acoustic/language model</li>
 *   <li><b>ភាសាអង់គ្លេស (English - en):</b> vosk-model-small-en-us-0.15 (~40MB)</li>
 *   <li><b>ភាសាចិន (Chinese - zh):</b> vosk-model-small-cn-0.22 (~42MB)</li>
 *   <li><b>ភាសាថៃ (Thai - th):</b> commonvoice-th small model (~45MB)</li>
 * </ul>
 *
 * <p>ដំណើរការ៖
 * <ul>
 *   <li>ពិនិត្យមើលភាសាបច្ចុប្បន្នដែលក្ដារចុចកំពុងប្រើ (ឬភាសាប្រព័ន្ធ)។</li>
 *   <li>ទាញយក Model សម្រាប់ភាសានោះលើកដំបូងបង្អស់តែម្ដងគត់ រក្សាទុកក្នុង storage SwiftKey។</li>
 *   <li>ដំណើរការ Offline ១០០% ជារៀងរហូត។</li>
 * </ul>
 */
public final class VoskModelManager {

    private static final String TAG = "VoskModelManager";

    /** ថតផ្ទុក Model មេក្នុង storage ខាងក្នុងរបស់ SwiftKey */
    private static final String MODEL_BASE_DIR = "vosk-models";

    /** បញ្ជី URLs សម្រាប់ Small Offline Models ទាំង ៤ ភាសា */
    private static final Map<String, String> MODEL_URLS = new HashMap<>();

    static {
        // English (US) Small Model (~40MB)
        MODEL_URLS.put("en", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip");
        // Chinese Small Model (~42MB)
        MODEL_URLS.put("zh", "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip");
        // Thai Small Model (~45MB)
        MODEL_URLS.put("th", "https://github.com/vistec-AI/commonvoice-th/releases/download/v1.0/vosk-model-small-th.zip");
        // Khmer Small Model
        MODEL_URLS.put("km", "https://github.com/moeunzinkh-debug/Swiftkey/releases/download/v1.0.0/vosk-model-small-km.zip");
    }

    private static final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VoskModelManager() {
    }

    /**
     * កំណត់កូដភាសាដែលត្រូវប្រើ (km, en, zh, th)។
     * ប្រសិនបើមិនស្គាល់ នឹងប្រើ "en" ជា default។
     */
    public static String resolveLanguageCode(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        if (MODEL_URLS.containsKey(lang)) {
            return lang;
        }
        return "en";
    }

    /**
     * ឈ្មោះភាសាសម្រាប់បង្ហាញជាអក្សរខ្មែរលើ Screen
     */
    public static String getLanguageName(String langCode) {
        switch (langCode) {
            case "km":
                return "ភាសាខ្មែរ (Khmer)";
            case "zh":
                return "ភាសាចិន (Chinese)";
            case "th":
                return "ភាសាថៃ (Thai)";
            case "en":
            default:
                return "ភាសាអង់គ្លេស (English)";
        }
    }

    /**
     * @return ថតឯកសារដែលត្រូវផ្ទុក Model តាមភាសានីមួយៗ
     */
    public static File getModelDir(Context context, String langCode) {
        File baseDir = new File(context.getFilesDir(), MODEL_BASE_DIR);
        return new File(baseDir, langCode);
    }

    /**
     * ពិនិត្យមើលថាតើ Model នៃភាសានោះត្រូវបានទាញយក និងត្រៀមរួចរាល់ហើយឬនៅ។
     */
    public static boolean isModelReady(Context context, String langCode) {
        File dir = getModelDir(context, langCode);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        File[] files = dir.listFiles();
        return files != null && files.length > 0;
    }

    /**
     * ចាប់ផ្ដើមទាញយក Model នៃភាសានោះលើកដំបូងក្នុង Background Thread (On-Demand)។
     */
    public static void ensureModelDownloaded(Context context, String requestedLang, Runnable onReadyCallback) {
        final String lang = (requestedLang != null && MODEL_URLS.containsKey(requestedLang))
                ? requestedLang : resolveLanguageCode(null);

        if (isModelReady(context, lang)) {
            if (onReadyCallback != null) {
                mainHandler.post(onReadyCallback);
            }
            return;
        }

        final String modelUrl = MODEL_URLS.get(lang);
        if (modelUrl == null) {
            return;
        }

        if (isDownloading.compareAndSet(false, true)) {
            String langName = getLanguageName(lang);
            showToast(context, "កំពុងរៀបចំ Voice Model សម្រាប់ " + langName + " (ទាញយកតែម្ដងគត់)...");

            new Thread(() -> {
                boolean success = false;
                try {
                    File targetDir = getModelDir(context, lang);
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }

                    File tempZip = new File(context.getCacheDir(), "vosk-" + lang + "-temp.zip");
                    Log.i(TAG, "Starting download of " + lang + " model from: " + modelUrl);

                    downloadFile(modelUrl, tempZip);
                    Log.i(TAG, "Download finished, extracting to: " + targetDir.getAbsolutePath());

                    unzip(tempZip, targetDir);
                    tempZip.delete();

                    success = isModelReady(context, lang);
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to download/unpack Vosk model for " + lang, e);
                } finally {
                    isDownloading.set(false);
                }

                final boolean ready = success;
                mainHandler.post(() -> {
                    if (ready) {
                        showToast(context, "Voice Model " + langName + " រួចរាល់! លោកអ្នកអាចប្រើ Mic បាន Offline រហូត។");
                        if (onReadyCallback != null) {
                            onReadyCallback.run();
                        }
                    } else {
                        showToast(context, "ការទាញយក Voice Model " + langName + " មិនជោគជ័យ។ សូមពិនិត្យមើលអ៊ីនធឺណិត។");
                    }
                });
            }, "Vosk-Model-Downloader-" + lang).start();
        } else {
            showToast(context, "កំពុងទាញយក Model... សូមរង់ចាំបន្តិច។");
        }
    }

    private static void downloadFile(String urlStr, File destination) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage());
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
        } finally {
            conn.disconnect();
        }
    }

    private static void unzip(File zipFile, File targetDirectory) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                int firstSlash = entryName.indexOf('/');
                String relativePath = (firstSlash != -1 && firstSlash < entryName.length() - 1)
                        ? entryName.substring(firstSlash + 1)
                        : entryName;

                if (relativePath.isEmpty()) {
                    continue;
                }

                File file = new File(targetDirectory, relativePath);
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void showToast(Context context, String message) {
        try {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
