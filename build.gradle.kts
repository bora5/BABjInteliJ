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
        // Target IntelliJ IDEA Ultimate 2026.2 (build 262), matching the developer's IDE.
        // If this fails to resolve (e.g. still EAP-only), pin the exact build instead:
        //   create("IU", "262.8665.258")
        intellijIdeaUltimate("2026.2")
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
