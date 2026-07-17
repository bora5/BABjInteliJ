plugins {
    id("java")
    // No version here: the plugin is put on the classpath by the settings plugin
    // (org.jetbrains.intellij.platform.settings in settings.gradle.kts). Requesting a
    // version in both places fails with "already on the classpath with an unknown version".
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

// Repositories are declared once, at the settings level (settings.gradle.kts).

dependencies {
    intellijPlatform {
        // 2024.3 runs on JDK 21. Community Edition is enough — we only use Java PSI.
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            // No upper bound: the plugin uses only stable APIs, so let it load on
            // current IDEs (e.g. 2026.2 / build 262) and future ones without a rebuild.
            untilBuild = provider { null }
        }
    }
}
