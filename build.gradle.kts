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
        // IntelliJ 2026.2 platform jars are compiled for Java 25 (class file 69), so the plugin
        // must be built with JDK 25 (Gradle auto-provisions it via the foojay resolver).
        languageVersion = JavaLanguageVersion.of(25)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Java-25 bytecode won't load on older IDEs (JBR 21), so require 2026.2 (build 262)+.
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}
