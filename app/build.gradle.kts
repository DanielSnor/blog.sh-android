import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.blogsh.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.blogsh.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The release as it will ship -- shrunk -- signed with the debug key,
        // to try on a device before there is a release key.
        create("shrunk") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // BouncyCastle and sshj both carry this one.
        excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
      }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // The screens are drawn on the desk, from the app's own resources.
        unitTests.isIncludeAndroidResources = true
    }
}

/**
 * The commit and the time of this build, for the lines at the foot of
 * the settings that tell one copy of the app from another: two lines in
 * a file that rides in the app. Written at every build -- the time is
 * the build's, not the last change's.
 */
abstract class BuildStampTask : DefaultTask() {
    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @get:Internal
    abstract val root: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    private fun git(vararg args: String): String {
        val said = ByteArrayOutputStream()
        val result = runCatching {
            exec.exec {
                commandLine(listOf("git", "-C", root.get().asFile.path) + args)
                standardOutput = said
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
        }.getOrNull()
        return if (result?.exitValue == 0) said.toString().trim() else ""
    }

    @TaskAction
    fun write() {
        var commit = git("rev-parse", "--short", "HEAD")
        // A plus for a tree that had changes not committed.
        if (commit.isNotEmpty() && git("status", "--porcelain").isNotEmpty()) commit += "+"
        val built = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        assets.get().asFile.resolve("BuildStamp.txt").writeText("$commit\n$built\n")
    }
}

val buildStamp = tasks.register<BuildStampTask>("buildStamp") {
    root.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(buildStamp, BuildStampTask::assets)
    }
}

// The Android the screens are drawn on comes through Gradle like every
// other dependency, and the tests are told where it lies: left to fetch
// it themselves they would go to the network on their own, past whatever
// Gradle was told about proxies and certificates.
val robolectricAndroid by configurations.creating

val robolectricAndroidDir = layout.buildDirectory.dir("robolectric-android")

val fetchRobolectricAndroid = tasks.register<Copy>("fetchRobolectricAndroid") {
    from(robolectricAndroid)
    into(robolectricAndroidDir)
}

tasks.withType<Test>().configureEach {
    dependsOn(fetchRobolectricAndroid)
    // The Android drawn on the desk reaches into the JVM where a JVM no longer lets it by itself.
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    )
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricAndroidDir.get().asFile.path)
    // The pictures are kept with the tests, not with what a build leaves
    // behind. A run compares the screens with them and fails where one has
    // changed; `-Pshots=record` takes the pictures anew instead.
    val shots = layout.projectDirectory.dir("src/test/shots").asFile
    val recording = providers.gradleProperty("shots").orNull == "record"
    systemProperty("blogsh.shots", shots.path)
    // Where a screen that has changed is drawn beside its picture, to be looked at.
    systemProperty("blogsh.shots.changed", layout.buildDirectory.dir("shots-changed").get().asFile.path)
    systemProperty("roborazzi.test.record", recording.toString())
    systemProperty("roborazzi.test.verify", (!recording).toString())
    inputs.dir(shots).withPropertyName("shots").optional()
    if (recording) outputs.upToDateWhen { false }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  // SSH. sshj asks for BouncyCastle by name at run time only, and the app
  // names its classes itself (the key, the provider), so it is said here too.
  implementation(libs.sshj)
  implementation(libs.bouncycastle)

  // A video made into what travels: what AVAssetExportSession does on iOS.
  implementation(libs.media3.transformer)
  implementation(libs.media3.effect)
  implementation(libs.media3.common)
  implementation(libs.media3.muxer)
  implementation(libs.media3.container)
  implementation(libs.media3.exoplayer)

  // The queue: a row carried to another position.
  implementation(libs.reorderable)

  // A pairing code read by the camera: the camera, and a reader of QR
  // codes that needs nothing of the device but the picture.
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.zxing.core)

  testImplementation(libs.junit)

  // The screens, drawn on the desk and compared with how they were last
  // looked at (tools/shots says how): Robolectric draws them, Roborazzi
  // keeps the pictures.
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  robolectricAndroid(libs.robolectric.android)
  testImplementation(libs.zxing.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.kotlinx.serialization.json)
}
