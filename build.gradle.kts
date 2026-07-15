plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")   // ili Ultimate ako ti treba JPA/Spring podrška
        bundledPlugin("com.intellij.java") // PSI za Javu
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "243" }
    }
}
