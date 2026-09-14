import com.android.build.api.dsl.ApplicationExtension

// Extension bundle "extensions/swiftkey.mpe" — ត្រូវ​បាន​ Morphe plugin ចាក់​បញ្ចូល​ទៅក្នុង
// classes*.dex របស់ APK គោល​នៅ​ពេល patch (មិនមែន​ APK ដាច់​ដោយឡែក)។
// គំរូ​តាម extensions/twitch របស់​គម្រោង​ដើម — ប្រើ Java តែប៉ុណ្ណោះ គ្មាន dependency បន្ថែម
// ដើម្បី​កុំ​ឲ្យ​បណ្តុំ​កូដ​បណ្ណាល័យ​ទៅ​ប៉ះទង្គិច​នឹង​កំណែ​ដែល​កម្មវិធីគោល​វេច​មក​ហើយ។
configure<ApplicationExtension> {
    namespace = "app.morphe.extension.swiftkey"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
