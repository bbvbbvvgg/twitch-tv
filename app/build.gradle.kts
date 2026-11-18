plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "su.expensive.twitchtv"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "su.expensive.twitchtv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations += listOf("ru", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/twitchtv.jks")
            storePassword = (project.findProperty("TV_STORE_PASS") ?: System.getenv("TV_STORE_PASS") ?: "").toString()
            keyAlias = "twitchtv"
            keyPassword = (project.findProperty("TV_STORE_PASS") ?: System.getenv("TV_STORE_PASS") ?: "").toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // A release must never silently fall back to the debug key: an APK signed with a
            // different key cannot be installed over the existing app. Debug signing only on
            // explicit request: ./gradlew assembleRelease -PallowDebugSigning=true
            val ks = rootProject.file("keystore/twitchtv.jks")
            val pass = (project.findProperty("TV_STORE_PASS") ?: System.getenv("TV_STORE_PASS"))?.toString()
            val allowDebug = project.findProperty("allowDebugSigning")?.toString() == "true"
            signingConfig = when {
                ks.exists() && !pass.isNullOrEmpty() -> signingConfigs.getByName("release")
                allowDebug -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { t ->
            t.systemProperty("live", System.getProperty("live") ?: "false")
            t.systemProperty("liveOut", rootProject.file("test-report/live-report.txt").absolutePath)
            t.testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
        }
    }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1") }
}

dependencies {
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Fail early with a clear message instead of producing an unsigned or debug-signed release.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.project == project && it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    if (wantsRelease) {
        val ks = rootProject.file("keystore/twitchtv.jks")
        val pass = (project.findProperty("TV_STORE_PASS") ?: System.getenv("TV_STORE_PASS"))?.toString()
        val allowDebug = project.findProperty("allowDebugSigning")?.toString() == "true"
        if (!allowDebug && (!ks.exists() || pass.isNullOrEmpty()))
            throw GradleException("Release-подпись: нужен keystore/twitchtv.jks и TV_STORE_PASS (см. SIGNING.md).")
    }
}
