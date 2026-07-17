plugins {
    id("java")
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
            untilBuild = provider { null }
        }
    }
}
