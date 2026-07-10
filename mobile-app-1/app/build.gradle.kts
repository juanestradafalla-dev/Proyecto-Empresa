import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val releaseSigningPropertiesFile = rootProject.file("app/signing/release-signing.properties")
val releaseSigningProperties = Properties()
val hasReleaseSigning = releaseSigningPropertiesFile.exists()

if (hasReleaseSigning) {
    releaseSigningProperties.load(FileInputStream(releaseSigningPropertiesFile))
}

android {
    namespace = "com.arlessas.gestion"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningProperties["storeFile"] as String)
                storePassword = releaseSigningProperties["storePassword"] as String
                keyAlias = releaseSigningProperties["keyAlias"] as String
                keyPassword = releaseSigningProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.arlessas.gestion"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "5.0.0"
        buildConfigField("boolean", "INCLUDE_TALLER", "false")
        buildConfigField("boolean", "TALLER_STANDALONE", "false")
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    
    // Firebase SDKs
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")

    // ML Kit Barcode Scanning
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Firebase callable functions for the OpenAI backend
    implementation("com.google.firebase:firebase-functions")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.14.0")
    
    // GSON for offline JSON storage
    implementation("com.google.code.gson:gson:2.14.0")
}
