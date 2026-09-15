import com.android.build.api.dsl.ApplicationExtension

// Morphe normally exports DEX only. Native voice libraries must also be exported as
// patch resources below, then copied into the TARGET APK by OfflineVoiceResourcesPatch.
configure<ApplicationExtension> {
    namespace = "app.morphe.extension.swiftkey"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                targets += "swiftkey_whisper"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val exportVoiceNativeLibraries = tasks.register<Sync>("exportVoiceNativeLibraries") {
    val merged = tasks.named("mergeReleaseNativeLibs")
    dependsOn(merged)
    from(merged.map { it.outputs.files.asFileTree }) {
        include("**/libswiftkey_whisper.so")
        eachFile { path = "lib/${file.parentFile.name}/${file.name}" }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("morphe/native/swiftkey"))
    doLast {
        val root = destinationDir
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val entries = abis.map { "lib/$it/libswiftkey_whisper.so" }
        entries.forEach { check(root.resolve(it).isFile) { "Missing voice native library: $it" } }
        root.resolve("index.txt").writeText(entries.joinToString("\n", postfix = "\n"))
    }
}

// The extensionConfiguration already exposes build/morphe to the patches module.
// Its DEX Sync target is the extensions/ subdirectory; our native/ sibling is preserved.
tasks.named("syncExtension") { dependsOn(exportVoiceNativeLibraries) }
