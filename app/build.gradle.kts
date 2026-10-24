import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingPropertiesPath = System.getenv("MMV_SIGNING_PROPERTIES")
val signingPropertiesFile = signingPropertiesPath?.let { File(it) }
val signingProperties = Properties().apply {
    if (signingPropertiesFile != null && signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.mathmotor.video"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mathmotor.video.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 394
        versionName = "8.28-timeline-trim-fix"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (signingProperties.isNotEmpty()) {
                val signingDirectory = signingPropertiesFile!!.parentFile
                storeFile = File(signingDirectory, signingProperties.getProperty("storeFile"))
                storeType = signingProperties.getProperty("storeType", "PKCS12")
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-transformer:1.10.1")
    implementation("androidx.media3:media3-effect:1.10.1")
    implementation("androidx.media3:media3-inspector-frame:1.10.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

// Trigger signed build to verify the restored updater and Media3 export APIs.
