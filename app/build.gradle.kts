// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val tidemarkVersionName = providers.gradleProperty("tidemark.versionName").get()
val tidemarkVersionCode = providers.gradleProperty("tidemark.versionCode").get().toInt()

// Release signing comes from the environment (GitHub secrets in CI, see docs/release.md).
// When any value is missing the release build stays unsigned.
// The names carry a "release" prefix on purpose: inside create("release") { } a bare keyAlias or
// keyPassword resolves to the signing config's own (null) property, not to a script-level val.
val releaseStorePath = providers.environmentVariable("TIDEMARK_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("TIDEMARK_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TIDEMARK_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("TIDEMARK_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "io.reddevs.tidemark"
    // compileSdk 37: current AndroidX (Compose 1.12, Navigation 2.10, Vico 3.3) needs it. targetSdk stays 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.reddevs.tidemark"
        minSdk = 28
        targetSdk = 36
        versionCode = tidemarkVersionCode
        versionName = tidemarkVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        // Version freshness is handled by hand in gradle/libs.versions.toml.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // targetSdk 36 is a deliberate design choice (see the Memory Bank decision log).
        disable += "OldTargetApi"
    }
}

/**
 * Copies the repo-root privacy policy into generated assets, so the in-app rationale screen and
 * the published policy come from the same files.
 */
abstract class CopyPrivacyPolicy : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("privacy")
        target.deleteRecursively()
        target.mkdirs()
        sources.files.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

val copyPrivacyPolicy = tasks.register<CopyPrivacyPolicy>("copyPrivacyPolicy") {
    sources.from(rootProject.file("PRIVACY.md"), rootProject.file("PRIVACY.fr.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyPrivacyPolicy, CopyPrivacyPolicy::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.health.connect.client)
    implementation(libs.vico.compose.m3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.health.connect.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
