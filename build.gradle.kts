
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    //id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("com.gradleup.shadow") version "9.0.0-rc2"
    //id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "me.hellrevenger"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    maven {
        url = uri("https://jitpack.io")
    }

    maven { url = uri("https://maven.fabricmc.net") }
}


//val highPriority by configurations.creating//= configurations.compileOnly.get()
//val lowPriority = configurations.implementation.get()
//configurations {
//    implementation.get().isCanBeResolved = true
//    sourceSets.main.get().compileClasspath = highPriority + sourceSets.main.get().compileClasspath + lowPriority
//}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
    }

    implementation("net.fabricmc:mapping-io:0.7.1")


//    implementation(kotlin("compiler-embeddable"))

//    compileOnly("org.jetbrains.kotlin:kotlin-compiler:2.1.0")
//    highPriority(files("${gradle.gradleUserHomeDir.absolutePath}\\caches\\${gradle.gradleVersion}\\$utilLocation"))

}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
      Initial version
    """.trimIndent()
    }
}

tasks {

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}