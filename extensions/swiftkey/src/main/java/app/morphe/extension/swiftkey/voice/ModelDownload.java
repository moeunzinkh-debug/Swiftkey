package app.morphe.extension.swiftkey.voice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Android-independent, bounded, checksum-verified model download. Audio never passes here. */
public final class ModelDownload {
    public static final String FILE_NAME = "ggml-base.bin";
    public static final long MODEL_BYTES = 147951465L;
    public static final String SHA256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe";
    public static final String URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/"
        + "5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin?download=true";

    public interface Progress {
        void update(long bytes);
        boolean isCancelled();
    }

    private ModelDownload() { }

    public static void download(File destination, Progress progress) throws IOException {
        URL url = new URL(URL);
        HttpURLConnection connection = null;
        try {
            // Follow only HTTPS redirects; never downgrade model integrity/transport to HTTP.
            for (int redirects = 0; redirects <= 6; redirects++) {
                if (progress.isCancelled()) throw new InterruptedIOException("Download cancelled");
                if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) {
                    throw new IOException("The model source must use HTTPS");
                }
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept-Encoding", "identity");
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_OK) {
                    long length = connection.getContentLengthLong();
                    if (length > 0 && length != MODEL_BYTES) {
                        throw new IOException("Unexpected model size");
                    }
                    try (InputStream input = connection.getInputStream()) {
                        install(input, destination, MODEL_BYTES, SHA256, progress);
                    }
                    return;
                }
                if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                    throw new IOException("Model download failed (HTTP " + status + ")");
                }
                String location = connection.getHeaderField("Location");
                if (location == null) throw new IOException("Missing model redirect");
                url = new URL(url, location);
                connection.disconnect();
                connection = null;
            }
            throw new IOException("Too many model redirects");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Never publish a partial/corrupt file, and never destroy an existing usable model on failure. */
    public static void install(InputStream input, File destination, long expectedBytes,
                               String expectedHash, Progress progress) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create model storage");
        }
        File temporary = new File(parent, destination.getName() + ".part");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        try {
            long total = 0;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (progress.isCancelled()) throw new InterruptedIOException("Download cancelled");
                    total += count;
                    if (total > expectedBytes) throw new IOException("Model exceeds the expected size");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    progress.update(total);
                }
                output.getFD().sync();
            }
            if (progress.isCancelled()) throw new InterruptedIOException("Download cancelled");
            if (total != expectedBytes || !hex(digest.digest()).equalsIgnoreCase(expectedHash)) {
                throw new IOException("Model verification failed; please download again");
            }
            // Same filesystem, API 26+. No incomplete model is ever visible to the recognizer.
            Files.move(temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }
}
