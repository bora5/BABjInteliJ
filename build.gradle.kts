plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

// Repositories are declared once, at the settings level (settings.gradle.kts).

dependencies {
    intellijPlatform {
        // Build against Community 2024.3 (a stable release that resolves and runs on JDK 21). The
        // plugin uses only stable core + Java PSI APIs, so — with no untilBuild cap — it loads on
        // current IDEs (2026.2 / build 262) and in Ultimate. Community 2026.2 is intentionally not
        // used: it does not resolve under this version string (still EAP-only for IC) and would
        // force a JDK 25 toolchain for no benefit.
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
            // No upper bound: only stable APIs are used, so it loads on 2026.2 (262) and later.
            untilBuild = provider { null }
        }
    }
}
