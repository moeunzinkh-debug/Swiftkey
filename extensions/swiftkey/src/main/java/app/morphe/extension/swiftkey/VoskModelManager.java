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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * អ្នកគ្រប់គ្រង Vosk Offline Speech Recognition Model សម្រាប់ SwiftKey។
 *
 * <p>ដំណើរការទាញយក On-Demand៖
 * <ul>
 *   <li>ពិនិត្យមើលថត Model ក្នុង Internal Storage (files/vosk-model)។</li>
 *   <li>ប្រសិនបើមិនទាន់មាន៖ ទាញយក Model zip ខ្នាតតូច (~40MB) ពី Remote Repository ដោយស្វ័យប្រវត្តិ។</li>
 *   <li>ពន្លា (Unpack) រក្សាទុកក្នុង Files Directory របស់ SwiftKey តែម្ដងគត់។</li>
 *   <li>រាល់លើកក្រោយៗទៀតដំណើរការ Offline ទាំងស្រុង មិនបាច់ភ្ជាប់អ៊ីនធឺណិតឡើយ។</li>
 * </ul>
 */
public final class VoskModelManager {

    private static final String TAG = "VoskModelManager";

    /** ថតផ្ទុក Model ក្នុង storage ខាងក្នុងរបស់ SwiftKey */
    private static final String MODEL_DIR_NAME = "vosk-model";

    /**
     * URL គំរូសម្រាប់ទាញយក Vosk Small Model (English / Multilingual lightweight model ~40MB)
     */
    private static final String DEFAULT_MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    private static final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VoskModelManager() {
    }

    /**
     * @return ថតឯកសារដែលត្រូវផ្ទុក Model
     */
    public static File getModelDir(Context context) {
        return new File(context.getFilesDir(), MODEL_DIR_NAME);
    }

    /**
     * ពិនិត្យមើលថាតើ Model ត្រូវបានទាញយក និងត្រៀមរួចរាល់ហើយឬនៅ។
     */
    public static boolean isModelReady(Context context) {
        File dir = getModelDir(context);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        // Model របស់ Vosk ត្រូវមាន subfolder ឬ file សំខាន់ៗដូចជា am/final.mdl ឬ mfcc.conf
        File[] files = dir.listFiles();
        return files != null && files.length > 0;
    }

    /**
     * ចាប់ផ្ដើមទាញយក Model លើកដំបូងក្នុង Background Thread (On-Demand)។
     */
    public static void ensureModelDownloaded(Context context, Runnable onReadyCallback) {
        if (isModelReady(context)) {
            if (onReadyCallback != null) {
                mainHandler.post(onReadyCallback);
            }
            return;
        }

        if (isDownloading.compareAndSet(false, true)) {
            showToast(context, "កំពុងរៀបចំ Voice Model ក្រៅបណ្ដាញ (ទាញយកតែម្ដងគត់)...");

            new Thread(() -> {
                boolean success = false;
                try {
                    File targetDir = getModelDir(context);
                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }

                    File tempZip = new File(context.getCacheDir(), "vosk-model-temp.zip");
                    Log.i(TAG, "Starting download of voice model from: " + DEFAULT_MODEL_URL);

                    downloadFile(DEFAULT_MODEL_URL, tempZip);
                    Log.i(TAG, "Download finished, extracting to: " + targetDir.getAbsolutePath());

                    unzip(tempZip, targetDir);
                    tempZip.delete();

                    success = isModelReady(context);
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to download/unpack Vosk model", e);
                } finally {
                    isDownloading.set(false);
                }

                final boolean ready = success;
                mainHandler.post(() -> {
                    if (ready) {
                        showToast(context, "Voice Model រួចរាល់! លោកអ្នកអាចប្រើ Mic បាន Offline រហូត។");
                        if (onReadyCallback != null) {
                            onReadyCallback.run();
                        }
                    } else {
                        showToast(context, "ការទាញយក Voice Model មិនជោគជ័យ។ សូមពិនិត្យមើលអ៊ីនធឺណិត។");
                    }
                });
            }, "Vosk-Model-Downloader").start();
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
            // ភាគច្រើន Vosk model zip មាន root folder (ឧ. vosk-model-small-en-us-0.15/...)
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                // កាត់ឈ្មោះ folder ដើមចេញ ប្រសិនបើ zip មាន root directory
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
