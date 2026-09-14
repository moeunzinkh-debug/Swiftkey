rootProject.name = "swiftkey-morphe-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                // មិន​ចាំបាច់​ដាក់ token នៅ​ក្នុង​ម៉ាស៊ីន​មូលដ្ឋាន​សម្រាប់​ public registry;
                // ក្នុង GitHub Actions ស្វ័យប្រវត្តិ ប្រើ GITHUB_ACTOR/GITHUB_TOKEN ដែល​បង្កើត​ឲ្យ​ស្រាប់។
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "1.3.2"
}

settings {
    extensions {
        defaultNamespace = "app.morphe.extension"
        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").toString())
    }
}
