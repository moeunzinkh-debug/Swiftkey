package app.morphe.extension.swiftkey;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.List;

/**
 * Fix មីក្រូហ្វូន/Voice typing សម្រាប់ SwiftKey នៅ​លើ​ឧបករណ៍​គ្មាន Google ។
 *
 * <p>ដើម​កំណើត​បញ្ហា៖ SwiftKey​ប្រើ​ Android SpeechRecognizer API របស់​ប្រព័ន្ធ (ជា​មុខងារ
 * "Google Voice Typing IME" ចាស់)។ នៅ​ពេល​មិន​មាន​ RecognitionService ណា​មួយ​ត្រូវ​បាន
 * តំឡើង វា​បង្ហាញ​សារ​ឲ្យ "ទាញយក Google Voice Search"។ ឧបករណ៍​ដែល​ប្រើ microG/គ្មាន GMS
 * មិនមាន​សេវា​នោះ ហើយ​ត្រូវ​បាន​បិទ​ដោយ​ចាត់ទុក​ថា​គ្មាន​សេវា​សម្គាល់​សំឡេង។
 *
 * <p>ដំណោះស្រាយ៖ បង្វែរ​រាល់​ការ​ហៅ​ API របស់ SwiftKey មក​កាន់​ថ្នាក់​នេះ (មើល
 * MicrophoneFixPatch)៖
 * <ul>
 *   <li>{@link #isRecognitionAvailable(Context)} ឆ្លើយ​ថា​មាន នៅ​ពេល​មាន
 *       RecognitionService ណា​មួយ​ក្នុង​ប្រព័ន្ធ (ឧ. Kõnele/Vosk, Speech Recognition
 *       &amp; Synthesis, សេវា​ក្នុង​ម៉ាស៊ីន​របស់​ក្រុមហ៊ុន)។</li>
 *   <li>{@link #createSpeechRecognizer(Context, ComponentName)} បោះបង់ ComponentName
 *       ដែល​ចង្អុល​ទៅ Google ពេល​វា​មិនមាន ហើយ​ប្រើ​សេវា​លំនាំដើម​របស់​ប្រព័ន្ធ
 *       (ការកំណត់ "ការ​បញ្ចូល​សំឡេង"/"Speech recognition provider") ជំនួស​វិញ។</li>
 *   <li>{@link #getPackageInfo(PackageManager, String, int)} ប្តូរ​ការ​រកមើល
 *       កញ្ចប់ Google មក​កាន់​កញ្ចប់​ផ្តល់​សេវា​សម្គាល់​សំឡេង​ជាក់ស្តែង ពេល
 *       កញ្ចប់ Google មិនមាន (បិទ​ការ​ត្រួតពិនិត្យ "ត្រូវតែ​ដំឡើង Google app")។</li>
 * </ul>
 *
 * <p><b>ចំណាំ៖</b> បំណះ​នេះ​មិន​បាន​បង្កើត​ម៉ាស៊ីន​សម្គាល់​សំឡេង​ដោយ​ខ្លួនឯង​ទេ។
 * វា​គ្រាន់តែ​ដក​ច្រក​របាំង Google ចេញ — អ្នក​ប្រើ​នៅ​តែ​ត្រូវ​ការ​កម្មវិធី RecognitionService
 * ណា​មួយ (Kõnele ប្រើ Vosk ដំណើរការ​ក្រៅ​បណ្ដាញ​ទាំងស្រុង; ឬ​កម្មវិធី Speech Recognition
 * &amp; Synthesis របស់ Google) ហើយ​កំណត់​វា​ជា​ម៉ាស៊ីន​លំនាំដើម​ក្នុង
 * Settings &gt; System &gt; Languages &amp; input &gt; Voice input។
 *
 * <p>រាល់​មេតូដ​ត្រូវ​រុំ​ដោយ​ប្រយ័ត្នប្រយែង មិន​បោះ​ចេញ​នូវ​ exception ណា​មួយ​ឲ្យ
 * ដំណើរការ​របស់ SwiftKey គាំង​ឡើយ (មេរៀន​ពី​ issue #196 របស់​គម្រោង​ដើម)។
 */
@SuppressWarnings("unused")
public final class MicSupport {

    /** កញ្ចប់ Google ដែល SwiftKey/ប្រព័ន្ធ​ចាស់ តែងតែ​យក​ទៅ​ធ្វើ​ជា​ម៉ាស៊ីន​សម្គាល់​សំឡេង។ */
    private static final String GOOGLE_SEARCH_APP = "com.google.android.googlequicksearchbox";
    private static final String GOOGLE_TTS_APP = "com.google.android.tts";

    private MicSupport() {
    }

    // ---------------------------------------------------------------------
    // តួឆ្លាស់​ថ្មី​សម្រាប់ SpeechRecognizer API
    // ---------------------------------------------------------------------

    /**
     * ឆ្លាស់​នៃ {@link SpeechRecognizer#isRecognitionAvailable(Context)}។
     *
     * <p>កំណែ​ដើម​គ្រាន់តែ​ពិនិត្យ​ថា​មាន​ម៉ាស៊ីន​លំនាំដើម (ជាទូទៅ​ Google) ឬអត់។
     * តួនេះ​ពិនិត្យ​ថា​មាន RecognitionService ណា​មួយ​ដែល​ប្រព័ន្ធ​អាច​ហៅបាន​ដែរឬទេ —
     * Kõnele/Vosk និង​សេវា​ឯករាជ្យ​ដទៃ​ទៀត​រាប់​បញ្ចូល​ទាំងអស់។
     */
    public static boolean isRecognitionAvailable(Context context) {
        try {
            if (context == null) {
                return false;
            }
            Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;

            if (hasRecognitionService(app)) {
                return true;
            }

            // ត្រឡប់​ទៅ​ការ​ត្រួតពិនិត្យ​ដើម​របស់​ប្រព័ន្ធ (ឧ. ករណី​មាន​ម៉ាស៊ីន​ដែល​មិន​បាន
            // ប្រកាស intent-filter តាម​ស្តង់ដារ)។
            return SpeechRecognizer.isRecognitionAvailable(app);
        } catch (Throwable ignored) {
            // មិនត្រូវ​បណ្តោយ​ឲ្យ​ក្តារចុច​គាំង​ជា​ដាច់ខាត។
            return false;
        }
    }

    /** ឆ្លាស់​នៃ {@link SpeechRecognizer#createSpeechRecognizer(Context)}។ */
    public static SpeechRecognizer createSpeechRecognizer(Context context) {
        return createSpeechRecognizer(context, null);
    }

    /**
     * ឆ្លាស់​នៃ {@code SpeechRecognizer.createSpeechRecognizer(Context, ComponentName)}
     * ដែល​ទទួល​យក​ម៉ាស៊ីន​ដែល​គេ​បញ្ជាក់​ជាក់លាក់។
     *
     * <p>ប្រសិនបើ ComponentName ចង្អុល​ទៅ Google ហើយ Google មិន​ត្រូវ​បាន​តំឡើង
     * (ឬ​ម៉ាស៊ីន​ដែល​បាន​ស្នើ​មិន​មាន) តម្លៃ​វា​ត្រូវ​បាន​ទម្លាក់ → ប្រព័ន្ធ​ជ្រើស
     * RecognitionService លំនាំដើម​ជំនួស ដែល​អាច​ជា​ម៉ាស៊ីន​ក្រៅ​បណ្ដាញ។
     */
    public static SpeechRecognizer createSpeechRecognizer(Context context,
                                                           ComponentName requested) {
        Context app = context.getApplicationContext() != null
            ? context.getApplicationContext() : context;

        ComponentName target = requested;
        try {
            if (target != null && (isGoogleRecognizer(target) || !serviceResolves(app, target))) {
                target = null;
            }

            if (target != null) {
                try {
                    return SpeechRecognizer.createSpeechRecognizer(app, target);
                } catch (Throwable ignored) {
                    // ធ្លាក់​មក​លំនាំដើម បើ​ម៉ាស៊ីន​ជាក់លាក់​បរាជ័យ។
                }
            }

            // ពិនិត្យមើល និងរៀបចំ Vosk Offline Model ប្រសិនបើគ្មានម៉ាស៊ីនសំឡេងលើប្រព័ន្ធ
            if (!hasRecognitionService(app)) {
                VoskModelManager.ensureModelDownloaded(app, null);
            }

            return SpeechRecognizer.createSpeechRecognizer(app);
        } catch (Throwable fallbackError) {
            // ការ​បង្កើត​លំនាំដើម​ក៏​បរាជ័យ — ព្យាយាម​តម្លៃ​ដើម​ជា​ចុងក្រោយ​បង្អស់
            // (ល្អ​ប្រសើរ​ជាង​បង្ខំ​គាំង)។
            try {
                if (requested != null) {
                    return SpeechRecognizer.createSpeechRecognizer(app, requested);
                }
            } catch (Throwable ignored) {
                // ធ្លាក់​មក​ខាងក្រោម។
            }
            if (fallbackError instanceof RuntimeException) {
                throw (RuntimeException) fallbackError;
            }
            throw new RuntimeException(fallbackError);
        }
    }

    // ---------------------------------------------------------------------
    // តួឆ្លាស់​សម្រាប់ PackageManager (បិទ​ទ្វារ "ត្រូវតែ​មាន Google app")
    // ---------------------------------------------------------------------

    /**
     * ឆ្លាស់​នៃ {@code PackageManager.getPackageInfo(String, int)} (invoke-virtual)។
     *
     * <p>នៅ​ពេល​កម្មវិធី​សួម​រក​កញ្ចប់ Google ដែល​មិនមាន នោះ​ត្រូវ​ឆ្លើយ​ដោយ
     * ព័ត៌មាន​កញ្ចប់​របស់​ម៉ាស៊ីន RecognitionService ជាក់ស្តែង​ជំនួស ជាជាង
     * បោះ {@link PackageManager.NameNotFoundException} (ដែល​នាំ​ទៅ​សារ
     * "ទាញយក Google Voice Search")។
     */
    public static android.content.pm.PackageInfo getPackageInfo(PackageManager manager,
                                                                String packageName,
                                                                int flags)
            throws PackageManager.NameNotFoundException {
        try {
            return manager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException original) {
            return handleGooglePackageMissing(manager, packageName, original,
                pkg -> manager.getPackageInfo(pkg, flags));
        }
    }

    /**
     * ឆ្លាស់​នៃ​ទម្រង់ Android 13+ {@code getPackageInfo(String, PackageInfoFlags)}។
     *
     * <p>ប្រភេទ​ប៉ារ៉ាម៉ែត្រ​ប្រកាស​ជា {@link Object} ដើម្បី​កុំ​ឲ្យ​បរិស្ថាន​ចងក្រង extension
     * ពឹង​ថ្នាក់ PackageInfoFlags ដែល​មាន​តែ​ក្នុង API 33+ (នៅ​ពេល​រត់ ART ផ្ទៀងផ្ទាត់​
     * ប៉ារ៉ាម៉ែត្រ​ជា​ប្រភេទ​រង​របស់ Object ជានិច្ច)។ ការ​ហៅ​មេតូដ​ថ្មី​ធ្វើ​តាម​រយៈ
     * reflection; មេតូដ​នេះ​ត្រូវ​បាន​ហៅ​តែ​លើ​ឧបករណ៍ API 33+ ប៉ុណ្ណោះ។
     */
    public static android.content.pm.PackageInfo getPackageInfo(PackageManager manager,
                                                                String packageName,
                                                                Object flags)
            throws PackageManager.NameNotFoundException {
        try {
            Class<?> flagsType = Class.forName("android.content.pm.PackageInfoFlags");
            java.lang.reflect.Method getPackageInfo =
                PackageManager.class.getMethod("getPackageInfo", String.class, flagsType);
            Object result = getPackageInfo.invoke(manager, packageName, flags);
            if (result instanceof android.content.pm.PackageInfo) {
                return (android.content.pm.PackageInfo) result;
            }
            throw new PackageManager.NameNotFoundException(
                "Unexpected getPackageInfo result for " + packageName);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PackageManager.NameNotFoundException) {
                return handleGooglePackageMissing(
                    manager,
                    packageName,
                    (PackageManager.NameNotFoundException) cause,
                    pkg -> {
                        try {
                            Class<?> flagsType = Class.forName("android.content.pm.PackageInfoFlags");
                            java.lang.reflect.Method retry =
                                PackageManager.class.getMethod("getPackageInfo", String.class, flagsType);
                            return (android.content.pm.PackageInfo) retry.invoke(manager, pkg, flags);
                        } catch (Throwable ignored) {
                            return null;
                        }
                    });
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause != null ? cause : e);
        } catch (PackageManager.NameNotFoundException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * តួ​ចែក​រំលែក៖ ពេល​ស្វែងរក​កញ្ចប់ Google មិនឃើញ សាក​ឆ្លើយ​ជំនួស​ដោយ​កញ្ចប់​
     * ម៉ាស៊ីន​សម្គាល់​សំឡេង​ជាក់ស្តែង; បើ​មិន​មាន​សូម​បោះ​កំហុស​ដើម​វិញ។
     */
    private interface PackageInfoLookup {
        android.content.pm.PackageInfo lookup(String packageName)
            throws PackageManager.NameNotFoundException;
    }

    private static android.content.pm.PackageInfo handleGooglePackageMissing(
            PackageManager manager,
            String packageName,
            PackageManager.NameNotFoundException original,
            PackageInfoLookup retry)
            throws PackageManager.NameNotFoundException {
        if (GOOGLE_SEARCH_APP.equals(packageName) || GOOGLE_TTS_APP.equals(packageName)) {
            String substitute = defaultRecognizerPackage(manager);
            if (substitute != null && !substitute.equals(packageName)) {
                android.content.pm.PackageInfo substituted = retry.lookup(substitute);
                if (substituted != null) {
                    return substituted;
                }
            }
        }
        throw original;
    }

    // ---------------------------------------------------------------------
    // ឧបករណ៍​ជំនួយ​ខាងក្នុង
    // ---------------------------------------------------------------------

    /** @return ពិត​បើ​ ComponentName ចង្អុល​ទៅ​ម៉ាស៊ីន​របស់ Google។ */
    private static boolean isGoogleRecognizer(ComponentName component) {
        String pkg = component.getPackageName();
        return GOOGLE_SEARCH_APP.equals(pkg) || GOOGLE_TTS_APP.equals(pkg);
    }

    /** @return ពិត​បើ​សេវា​ដែល​ចង្អុល​ដោយ ComponentName មាន​ពិត​នៅ​ក្នុង​ប្រព័ន្ធ។ */
    private static boolean serviceResolves(Context context, ComponentName component) {
        try {
            Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
            intent.setComponent(component);
            List<ResolveInfo> resolved =
                context.getPackageManager().queryIntentServices(intent, 0);
            return resolved != null && !resolved.isEmpty();
        } catch (Throwable ignored) {
            // ខ្វះ​ការ​មើលឃើញ​កញ្ចប់ (<queries>) ឬ​បញ្ហា​ប្រព័ន្ធ → សន្មត់​ថា​មិនមាន
            // ដើម្បី​ឲ្យ​វា​ត្រឡប់​មក​លំនាំដើម ដែល​មាន​សុវត្ថិភាព​ជាង។
            return false;
        }
    }

    /** @return ពិត​បើ​ប្រព័ន្ធ​មាន RecognitionService ណា​មួយ​អាច​ហៅ​បាន។ */
    private static boolean hasRecognitionService(Context context) {
        try {
            Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
            List<ResolveInfo> resolved =
                context.getPackageManager().queryIntentServices(intent, 0);
            return resolved != null && !resolved.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * @return កញ្ចប់​របស់​សេវា​សម្គាល់​សំឡេង​ដំបូង​គេ​ដែល​ប្រព័ន្ធ​រកឃើញ
     *         (ការ​មើលឃើញ​ត្រូវ​បាន​បើក​ដោយ &lt;queries&gt; ក្នុង manifest)។
     */
    private static String defaultRecognizerPackage(PackageManager manager) {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            List<ResolveInfo> activities = manager.queryIntentActivities(intent, 0);
            if (activities != null) {
                for (ResolveInfo info : activities) {
                    if (info.activityInfo != null && info.activityInfo.packageName != null
                            && !GOOGLE_SEARCH_APP.equals(info.activityInfo.packageName)) {
                        return info.activityInfo.packageName;
                    }
                }
                if (!activities.isEmpty() && activities.get(0).activityInfo != null) {
                    return activities.get(0).activityInfo.packageName;
                }
            }

            Intent serviceIntent = new Intent(RecognitionService.SERVICE_INTERFACE);
            List<ResolveInfo> services = manager.queryIntentServices(serviceIntent, 0);
            if (services != null && !services.isEmpty() && services.get(0).serviceInfo != null) {
                return services.get(0).serviceInfo.packageName;
            }
        } catch (Throwable ignored) {
            // ស្ងាត់ៗ​ត្រឡប់ null។
        }
        return null;
    }
}
