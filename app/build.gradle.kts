// Must be a top-level import: inside the script, "java.util..." resolves against Gradle's own
// `java` extension instead of the JDK package, and fails with "Unresolved reference: util".
import java.util.concurrent.TimeUnit

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Runs a git command and returns its trimmed output, or "" if git is missing, this is not a
 * checkout, or the command fails. Version numbers must never break a build.
 *
 * Deliberately NOT `providers.exec`. Gradle caches that provider's result and re-runs the
 * command only when its declared inputs change - and the command line here never changes, so
 * the value went stale and stuck. Observed on 2026-09-14: three commits and a clean tree later,
 * builds were still stamping "1.9 (85d8fbf+dirty)", pinning APKs to a commit they were not
 * built from and quietly defeating the whole point of deriving the version from git.
 *
 * A plain ProcessBuilder is re-run every configuration. That costs three git invocations per
 * build and gives up configuration-cache compatibility, which this project does not use.
 */
fun gitOutput(vararg args: String): String = runCatching {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(rootDir)
        .start()
    // Read before waiting: a process that fills its output pipe blocks until someone drains it.
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching ""
    }
    if (process.exitValue() == 0) output else ""
}.getOrDefault("")

// The version is derived from git rather than hand-edited, so every merge ships a build that
// can be told apart from the last one without anyone remembering to bump a number.
//   versionCode — the commit count. Only ever goes up, which is what Android requires.
//   versionName — "1.<commit count> (<short sha>)", e.g. "1.3 (6bc3343)". The sha is what
//                 makes a build traceable back to an exact commit on GitHub.
// A "+dirty" marker means the APK was built with uncommitted edits in the working tree, so
// what is on the watch is *not* what is on GitHub.
val gitCommitCount: Int? = gitOutput("rev-list", "--count", "HEAD").toIntOrNull()
val gitShortSha: String = gitOutput("rev-parse", "--short", "HEAD")
val gitDirtyMarker: String = if (gitOutput("status", "--porcelain").isNotEmpty()) "+dirty" else ""

android {
    namespace = "com.shieldrj.schoolperiod"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shieldrj.schoolperiod"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount ?: 1
        versionName = if (gitCommitCount != null && gitShortSha.isNotEmpty()) {
            "1.$gitCommitCount ($gitShortSha$gitDirtyMarker)"
        } else {
            // Building from a source copy with no git history.
            "1.0.0-dev"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        // Needed so the app can show its own version on screen.
        buildConfig = true
    }
}

dependencies {
    // Wear OS Core and Complications API
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Dynamic (clock-driven) complication values, used for the live progress ring.
    // Version matches what the complications library above resolves to.
    implementation("androidx.wear.protolayout:protolayout-expression:1.0.0-beta01")

    // Wear OS Compose (Material3 & Foundation)
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha26")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")

    // AndroidX & Compose standard
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // repeatOnLifecycle, used to stop the clock ticker when the app is off screen.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui:1.7.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.4")
    implementation("androidx.compose.material:material-icons-extended:1.7.4")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.4")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
