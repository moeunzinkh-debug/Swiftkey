package app.morphe.extension.swiftkey;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import app.morphe.extension.swiftkey.voice.OfflineRecognitionService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** Routes SwiftKey's Android speech path to OUR service. No Google app, external provider or GmsCore. */
@SuppressWarnings("unused")
public final class MicSupport {
    private MicSupport() { }

    public static boolean isRecognitionAvailable(Context context) {
        if (context == null) return false;
        try {
            return context.getPackageManager().getServiceInfo(
                new ComponentName(context, OfflineRecognitionService.class), 0).enabled;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    public static SpeechRecognizer createSpeechRecognizer(Context context) {
        return createSpeechRecognizer(context, null);
    }

    public static SpeechRecognizer createSpeechRecognizer(Context context, ComponentName ignored) {
        // Creating a recognizer does NOT download a model or start the microphone.
        return SpeechRecognizer.createSpeechRecognizer(context,
            new ComponentName(context, OfflineRecognitionService.class));
    }

    public static PackageInfo getPackageInfo(PackageManager manager, String name, int flags)
            throws PackageManager.NameNotFoundException {
        try {
            return manager.getPackageInfo(name, flags);
        } catch (PackageManager.NameNotFoundException original) {
            String ownPackage = substitute(manager, name);
            if (ownPackage == null) throw original;
            return manager.getPackageInfo(ownPackage, flags);
        }
    }

    /** API 33 flags are a NESTED PackageManager class. Keep Object in the hook ABI for API 26. */
    public static PackageInfo getPackageInfo(PackageManager manager, String name, Object flags)
            throws PackageManager.NameNotFoundException {
        try {
            return lookup(manager, name, flags);
        } catch (PackageManager.NameNotFoundException original) {
            String ownPackage = substitute(manager, name);
            if (ownPackage == null) throw original;
            return lookup(manager, ownPackage, flags);
        }
    }

    private static PackageInfo lookup(PackageManager manager, String name, Object flags)
            throws PackageManager.NameNotFoundException {
        try {
            Class<?> type = Class.forName("android.content.pm.PackageManager$PackageInfoFlags");
            Method method = PackageManager.class.getMethod("getPackageInfo", String.class, type);
            return (PackageInfo) method.invoke(manager, name, flags);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PackageManager.NameNotFoundException) throw (PackageManager.NameNotFoundException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Package query failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PackageInfoFlags is unavailable", e);
        }
    }

    private static String substitute(PackageManager manager, String requested) {
        if (!"com.google.android.googlequicksearchbox".equals(requested)
                && !"com.google.android.tts".equals(requested)) return null;
        List<ResolveInfo> services = manager.queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        if (services == null) return null;
        for (ResolveInfo service : services) {
            if (service.serviceInfo != null
                    && OfflineRecognitionService.class.getName().equals(service.serviceInfo.name)) {
                return service.serviceInfo.packageName;
            }
        }
        return null;
    }
}
