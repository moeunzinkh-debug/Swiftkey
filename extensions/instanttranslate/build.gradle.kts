import com.android.build.api.dsl.ApplicationExtension

// Extension bundle "extensions/instanttranslate.mpe" — ត្រូវ​បាន Morphe plugin ចាក់​បញ្ចូល
// ទៅក្នុង classes*.dex របស់ APK គោល​នៅ​ពេល patch (មិនមែន APK ដាច់​ដោយឡែក)។
extension {
    name = "extensions/instanttranslate.mpe"
}

configure<ApplicationExtension> {
    namespace = "app.morphe.extension.instanttranslate"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
