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
        applicationId = "com.mathmotor.video"
        minSdk = 26
        targetSdk = 35
        versionCode = 369
        versionName = "8.3-stable-signing"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
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
    testImplementation("junit:junit:4.13.2")
}
