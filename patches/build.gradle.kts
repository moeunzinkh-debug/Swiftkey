// TODO(ផ្ទាល់ខ្លួន): ប្តូរ​ឲ្យ​ត្រូវ​នឹង group របស់​អ្នក​នៅ gradle.properties ផងដែរ
group = "io.github.yourusername"

patches {
    about {
        // ឈ្មោះ​bundle ដែល​នឹង​បង្ហាញ​ក្នុង Morphe Manager
        name = "SwiftKey Morphe Patches"
        description = "Personal Morphe patches for Microsoft SwiftKey Keyboard."
        // TODO(ផ្ទាល់ខ្លួន): ដាក់ URL repo ពិត​របស់​អ្នក (ប្តូរ yourusername/swiftkey-morphe-patches)
        source = "https://github.com/yourusername/swiftkey-morphe-patches.git"
        author = "yourusername"
        contact = "na"
        website = "https://github.com/yourusername/swiftkey-morphe-patches"
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

    register<JavaExec>("diagnoseSmali") {
        description = "បណ្តោះអាសន្ន៖ សាក InlineSmaliCompiler ជាមួយស្នាម smali របស់បំណះ microG"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.DiagnoseSmaliKt")
    }

    // ប្រើ​ដោយ gradle-semantic-release-plugin
    publish {
        dependsOn("generatePatchesList")
    }
}
