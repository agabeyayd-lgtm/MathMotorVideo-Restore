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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mathmotor.video.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 385
        versionName = "8.19-responsive-studio"
        testInstrumentationRunner = "android.test.AndroidJUnitRunner"
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
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    testImplementation("junit:junit:4.13.2")
}