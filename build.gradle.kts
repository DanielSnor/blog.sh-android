// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

// A working copy may live in a folder something syncs (iCloud, Dropbox), and
// a build writes thousands of files that have no business there. A line
// `build.root=/some/dir` in local.properties puts every module's output
// under that directory instead of beside the sources.
val buildRoot: String? = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
  java.util.Properties().apply { file.inputStream().use { load(it) } }.getProperty("build.root")
}
if (buildRoot != null) {
  allprojects { layout.buildDirectory.set(file("$buildRoot/${project.name}")) }
}
