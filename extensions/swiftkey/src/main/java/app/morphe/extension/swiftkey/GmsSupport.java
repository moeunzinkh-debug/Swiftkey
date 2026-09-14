package app.morphe.extension.swiftkey;

import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * កូដ​គាំទ្រ​សម្រាប់​ក្រុម​បំណះ GmsCore/microG (MicroG RE, កញ្ចប់ app.revanced.android.gms)។
 *
 * <ul>
 *   <li>{@link #ensureAccountPermissions(Activity)} — ស្នើ​សិទ្ធិ​ចូល​គណនី​ microG
 *       (GET_ACCOUNTS និង EXTENDED_ACCESS) ជំនួស​ឲ្យ​ការ​ភ្ជាប់​ពី​ខាង​ក្នុង​កម្មវិធី
 *       ដែល​មិន​ដែល​ស្នើ​ពេល​រត់​លើ​ microG។</li>
 *   <li>{@link #currentProcessName()} — ប្រគល់​ឈ្មោះ​ដំណើរការ​ពិត​ដោយ​កាត់​បច្ច័យ
 *       ក្លូន (".morphe") ចេញ ទើប​តារាង switch(routing) ដែល​កម្មវិធី​ចងក្រង​ពី​ឈ្មោះ
 *       ដំណើរការ​នៅ​តែ​ត្រូវគ្នា (ការ​គាំទ្រ Clone)។</li>
 * </ul>
 *
 * <p>គ្រប់​មេតូដ​មិន​បោះ​ exception ចេញ​ទៅ​ក្រៅ — វា​នឹង​ត្រូវ​បាន​ហៅ​ក្នុង
 * ផ្លូវ​ចាប់ផ្តើម​របស់​គ្រប់ Activity ហើយ​មិន​អាច​ធ្វើ​ឲ្យ​ក្តារចុច​គាំង​បាន​ឡើយ។
 */
@SuppressWarnings("unused")
public final class GmsSupport {

    /**
     * បច្ច័យ​ដែល​បំណះ "Clone app" របស់ Morphe បន្ថែម​ពី​ក្រោយ​កញ្ចប់​ក្លូន
     * (មើល MorpheApp/morphe-patches all/misc/clone/CloneAppPatch)។
     */
    private static final String CLONE_SUFFIX = ".morphe";

    /**
     * សិទ្ធិ​ពិសេស​របស់ microG GmsCore — GmsCore RE ប្រកាស​វា​ជា​សិទ្ធិ "dangerous"
     * ដែល​ភាគី​ទីបី​ (កម្មវិធី​ដែល​គេ​ចុះហត្ថលេខា​ថ្មី) ត្រូវ​ស្នើ​នៅ​ពេល​រត់។
     */
    private static final String PERMISSION_EXTENDED_ACCESS = "app.revanced.gms.EXTENDED_ACCESS";
    private static final String PERMISSION_GET_ACCOUNTS = "android.permission.GET_ACCOUNTS";

    /** លេខ​សំណើ​គ្មាន​ន័យ​តប​ផ្ទាល់ខ្លួន (ដូច​ការ​អនុវត្ត​គំរូ GmsCore)។ */
    private static final int REQUEST_CODE = 0x6d47;

    private static final AtomicBoolean REQUEST_ATTEMPTED = new AtomicBoolean(false);

    private GmsSupport() {
    }

    // ---------------------------------------------------------------------
    // MicroG Account Permissions
    // ---------------------------------------------------------------------

    /**
     * ត្រូវ​បាន​បញ្ចូល​ទៅ​ដើម onCreate(Bundle) របស់​គ្រប់ Activity ក្នុង​កម្មវិធី
     * (ដោយ AccountPermissionsPatch)។ ស្នើ​សិទ្ធិ​ម្ដងគត់​សម្រាប់​មួយ​ដំណើរការ
     * ដើម្បី​កុំ​ឲ្យ​ប្រអប់​សុំ​សិទ្ធិ​លេច​ម្ដង​ហើយ​ម្ដង​ទៀត។
     */
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
            // ប្រអប់​សិទ្ធិ​មិន​អាច​បើក​បាន (ឧ. មិនមាន GmsCore) — បន្ត​ដំណើរការ​ធម្មតា។
        }
    }

    // ---------------------------------------------------------------------
    // Process Name Spoofing for Clone Support
    // ---------------------------------------------------------------------

    /**
     * ឆ្លាស់​នៃ Application.getProcessName() / ActivityThread.currentProcessName()។
     *
     * <p>ប្រគល់​ឈ្មោះ​ដំណើរការ​ពិត ប៉ុន្តែ​កាត់ ".morphe" ចេញ​ពី​ផ្នែក​ឈ្មោះ​កញ្ចប់
     * (រក្សា​ផ្នែក​ក្រោយ ":" ទុក ដូច​ជា ":googleapp"/":pushservice")។ ឧ.
     * {@code com.touchtype.swiftkey.morphe:settings} →
     * {@code com.touchtype.swiftkey:settings}។
     */
    public static String currentProcessName() {
        try {
            String processName = readRealProcessName();
            return sanitize(processName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** កាត់​បច្ច័យ​ក្លូន — ត្រូវ​បាន​បំបែក​ជា​មេតូដ​ឯករាជ្យ​ដើម្បី​សាកល្បង​ងាយស្រួល។ */
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

    /**
     * អាន​ឈ្មោះ​ដំណើរការ​តាម​លំដាប់​ដែល​ឆបគ្នា​ទាំង API ចាស់/ថ្មី៖
     * 1) Application.getProcessName() (API 28+) តាម​រយៈ​ការ​ឆ្លុះ (មិន​ចង​ផ្ទាល់
     *    ដើម្បី​ឲ្យ​កូដ​នៅ​ verify បាន​លើ API 26/27)
     * 2) ActivityThread.currentProcessName() (វិធី​របស់ AndroidX)
     * 3) អាន /proc/self/cmdline ត្រង់​មុន​ពហុ​ដំណើរការ។
     */
    private static String readRealProcessName() {
        // 1) Application.getProcessName() API 28+
        try {
            Method getProcessName = Application.class.getMethod("getProcessName");
            Object result = getProcessName.invoke(null);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Throwable ignored) {
            // API < 28 ឬ​វិធី​មិន​មាន។
        }

        // 2) ActivityThread.currentProcessName() (hidden API)
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method current = activityThread.getDeclaredMethod("currentProcessName");
            current.setAccessible(true);
            Object result = current.invoke(null);
            if (result instanceof String && !((String) result).isEmpty()) {
                return (String) result;
            }
        } catch (Throwable ignored) {
            // ត្រូវ​បាន​បិទ/មិន​មាន។
        }

        // 3) /proc/self/cmdline
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
            // ធ្លាក់​មក null។
        }

        return null;
    }
}
