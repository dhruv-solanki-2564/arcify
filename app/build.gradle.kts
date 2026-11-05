import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing (M7). Values come from a git-ignored `keystore.properties` in the repo root,
 * or from environment variables in CI. The keystore itself never enters the repo.
 *
 *   storeFile=C:/path/to/arcify-release.jks     (env ARCIFY_KEYSTORE)
 *   storePassword=…                             (env ARCIFY_KEYSTORE_PASSWORD)
 *   keyAlias=arcify                             (env ARCIFY_KEY_ALIAS)
 *   keyPassword=…                               (env ARCIFY_KEY_PASSWORD)
 */
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "ARCIFY_KEYSTORE")

/** Version from `-Parcify.versionName=1.2.3` (the release workflow passes the tag). */
val appVersionName = (findProperty("arcify.versionName") as String?) ?: "0.1.0"

/** 1.2.3 -> 10203, so every release tag gets a higher code than the last. */
val appVersionCode = (findProperty("arcify.versionCode") as String?)?.toInt() ?: run {
    val parts = appVersionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    (parts[0] * 10_000 + parts[1] * 100 + parts[2]).coerceAtLeast(1)
}

android {
    namespace = "app.arcify"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.arcify"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "ARCIFY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ARCIFY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ARCIFY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key the APK is left unsigned, except for a local smoke test with
            // `-Parcify.debugSignRelease=true` (debug key; never publish such a build).
            signingConfig = signingConfigs.findByName("release")
                ?: if (findProperty("arcify.debugSignRelease") == "true") signingConfigs.getByName("debug") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":blocker"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
}
