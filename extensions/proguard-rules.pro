# ច្បាប់ ProGuard/R8 សម្រាប់ "extension" (កូដ Java ផ្ទាល់​ដែល​ចាក់​បញ្ចូល​ទៅ​ក្នុង APK គោល)។
#
# ថ្នាក់ extension ត្រូវ​បាន​បញ្ចូល​គ្នា​ទៅក្នុង dex របស់​កម្មវិធីគោល​ដែល​ខ្លួនឯង​ត្រូវ​បាន
#​ពន្លិច (obfuscate) រួចហើយ — ថ្នាក់​របស់​គេ​កាន់កាប់​ឈ្មោះ​ឫស​ទទេ (a, b, c, ...)។
# បើ R8 ដាក់​ឈ្មោះ​ថ្នាក់​ extension ឲ្យ​ដូចគ្នា វា​ប៉ះទង្គិច (collision) ជាមួយ​ថ្នាក់​គោល
# ហើយ ART បដិសេធ​ការ verify ពេល​បញ្ចូល​គ្នា។ រក្សា​ឈ្មោះ​ពេញ​គ្រប់​ថ្នាក់​ extension ជានិច្ច។
#
# ខ្លឹមសារ​ដើម និង​មេរៀន​បញ្ហា​ collision (#196) យក​ពីគម្រោង​ដើម។
# រក្សា​គ្រប់ extension packages (swiftkey + simsimi) — កុំ​ឲ្យ R8 obfuscate ឈ្មោះ​ថ្នាក់
# ដែល​ត្រូវ​បាន​យោង​ក្នុង​បំណះ​តាម EXTENSION descriptor។
-keep class app.morphe.extension.** { *; }

# កុំ obfuscate / optimize — តាម​គម្រោង​ដើម Morphe patches
-dontobfuscate
-dontoptimize
-keepattributes *

# ថ្នាក់​ដែល R8 សំយោគ (lambda, desugaring) ត្រូវ​បង្ខំ​ឲ្យ​នៅ​ក្នុង package extension
# ដើម្បី​កុំឲ្យ​ធ្លាក់​មក​កាន់កាប់​ឈ្មោះ​ឫស​ទទេ។ ប្រើ root package ដើម្បី​គាំទ្រ
# extension ច្រើន (swiftkey, simsimi) ដោយ​មិន​ប៉ះទង្គិច​គ្នា។
-repackageclasses 'app.morphe.extension'

# Kotlin intrinsics — ត្រូវការ​សម្រាប់ extension ដែល​សរសេរ​ជា Java ប៉ុន្តែ​ប្រើ​ lambda
-keep class kotlin.jvm.internal.Intrinsics { public static *; }
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.lang.model.element.Modifier
