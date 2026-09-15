group = "io.github.moeunzinkhdebug"

patches {
    about {
        // ឈ្មោះ​bundle ដែល​នឹង​បង្ហាញ​ក្នុង Morphe Manager
        name = "SwiftKey Editing & Offline Voice"
        description = "Text editing and on-demand offline voice patches for Microsoft SwiftKey, compatible with Morphe."
        source = "https://github.com/moeunzinkh-debug/Swiftkey.git"
        author = "moeunzinkh-debug"
        contact = "na"
        website = "https://github.com/moeunzinkh-debug/Swiftkey"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// ញែក classpath ដើម្បី​ឲ្យ gson ប្រើ​បាន​ពេល​បង្កើត patches-list ប៉ុន្តែ​មិន​ត្រូវ​វេច​ចូល​ទៅក្នុង APK
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // ប្រើ​ដោយ gradle-semantic-release-plugin
    publish {
        dependsOn("generatePatchesList")
    }
}
