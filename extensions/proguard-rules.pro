# ច្បាប់ ProGuard/R8 សម្រាប់ "extension" (កូដ​ផ្ទាល់ដែល​វេច​បញ្ចូល​ទៅក្នុង APK គោល)។
#
# ថ្នាក់ extension ត្រូវ​បាន​បញ្ចូល​គ្នា​ទៅក្នុង dex របស់​កម្មវិធីគោល​ដែល​ខ្លួនឯង​ត្រូវ​បាន​ពន្លិច (obfuscate)
# រួចហើយ — ថ្នាក់​របស់​គេ​កាន់កាប់​ឈ្មោះ​ឫស​ទទេ (a, b, c, ...)។ បើ​ R8 ដាក់​ឈ្មោះ​ថ្នាក់​ extension
# ឲ្យ​ដូចគ្នា វា​នឹង​ប៉ះទង្គិច​ឈ្មោះ (collision) ជាមួយ​ថ្នាក់​គោល ហើយ ART នឹង​បដិសេធ​ការ verify
# នៅ​ពេល​បញ្ចូល​គ្នា។ រក្សា​ថ្នាក់​ extension ត្រង់​ឈ្មោះ​ពេញ​ជានិច្ច។
#
# ឥឡូវ​នេះ​គម្រោង​មិនទាន់​មាន extension ទេ (បំណះ SwiftKey កែ​តែ bytecode/resources) ប៉ុន្តែ
# settings.gradle.kts យោង​ឯកសារ​នេះ ទុក​វា​សម្រាប់​ពេល​បន្ថែម​កូដ​ផ្ទាល់​នៅ​ពេល​ក្រោយ។
-keep class app.morphe.extension.** { *; }

# ថ្នាក់​ដែល R8 សំយោគ (lambda, desugaring) ត្រូវ​បង្ខំ​ឲ្យ​នៅ​ក្នុង package extension
# ដើម្បី​កុំឲ្យ​ធ្លាក់​មក​កាន់កាប់​ឈ្មោះ​ឫស​ទទេ។ ត្រូវ​ប្តូរ package ខាងក្រោម​តាម extension ជាក់លាក់
# ពេល​អ្នក​បន្ថែម​វា (ឧ. app.morphe.extension.swiftkey)។
-repackageclasses 'app.morphe.extension'
