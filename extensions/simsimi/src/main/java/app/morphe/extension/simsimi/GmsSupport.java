package app.morphe.extension.simsimi;

import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public final class GmsSupport {

    private static final String CLONE_SUFFIX = ".morphe";

    private static final String PERMISSION_EXTENDED_ACCESS = "app.revanced.gms.EXTENDED_ACCESS";
    private static final String PERMISSION_GET_ACCOUNTS = "android.permission.GET_ACCOUNTS";

    private static final int REQUEST_CODE = 0x6d47;

    private static final AtomicBoolean REQUEST_ATTEMPTED = new AtomicBoolean(false);

    private GmsSupport() {
    }

    public static void ensureAccountPermissions(Activity activity) {
        try {
            if (activity == null || !REQUEST_ATTEMPTED.compareAndSet(false, true)) {
                return;
            }

            List<String> requested = new ArrayList<>(2);
            if (activity.checkSelfPermission(PERMISSION_EXTENDED_ACCESS)
                    != PackageManager.PERMISSION_GRANTED) {
                requested.add(PERMISSION_EXTENDED_ACCESS);
            }
            if (activity.checkSelfPermission(PERMISSION_GET_ACCOUNTS)
                    != PackageManager.PERMISSION_GRANTED) {
                requested.add(PERMISSION_GET_ACCOUNTS);
            }
            if (requested.isEmpty()) {
                return;
            }

            activity.requestPermissions(requested.toArray(new String[0]), REQUEST_CODE);
        } catch (Throwable ignored) {
        }
    }

    public static String currentProcessName() {
        try {
            String processName = readRealProcessName();
            return sanitize(processName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String sanitize(String processName) {
        if (processName == null || !processName.contains(CLONE_SUFFIX)) {
            return processName;
        }
        int colon = processName.indexOf(':');
        String base = colon < 0 ? processName : processName.substring(0, colon);
        String suffix = colon < 0 ? "" : processName.substring(colon);

        if (base.endsWith(CLONE_SUFFIX)) {
            base = base.substring(0, base.length() - CLONE_SUFFIX.length());
        }
        return base + suffix;
    }

    private static String readRealProcessName() {
        try {
            Method getProcessName = Application.class.getMethod("getProcessName");
            Object result = getProcessName.invoke(null);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method current = activityThread.getDeclaredMethod("currentProcessName");
            current.setAccessible(true);
            Object result = current.invoke(null);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Throwable ignored) {
        }

        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            char[] buffer = new char[256];
            int read = reader.read(buffer);
            if (read > 0) {
                String raw = new String(buffer, 0, read);
                int nul = raw.indexOf('\0');
                if (nul >= 0) {
                    raw = raw.substring(0, nul);
                }
                if (!raw.isEmpty()) {
                    return raw.trim();
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
