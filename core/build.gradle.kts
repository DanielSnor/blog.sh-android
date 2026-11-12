import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// What the apps ask for: `app.blogsh:core`, which the build including this one
// takes from here.
group = "app.blogsh"

// The same rule as the Android app's: a line `build.root=/some/dir` in the
// working copy's local.properties puts the output there, out of the folder
// something syncs.
val buildRoot: String? = rootDir.resolve("../local.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }.getProperty("build.root")
}
if (buildRoot != null) layout.buildDirectory.set(file("$buildRoot/core"))

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // The JDK it is compiled with is newer than what a phone has: only what Java 17 knows.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // The model's state is what the screens watch, on either system.
    api(libs.androidx.compose.runtime)
    implementation(libs.sshj)
    implementation(libs.bouncycastle)
    // A pairing code read from a picture of it.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
