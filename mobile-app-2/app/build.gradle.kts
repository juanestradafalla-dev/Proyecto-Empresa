import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

val googleServicesFile = project.file("google-services.json")
val releaseSigningPropertiesFile = rootProject.file("app/signing/release-signing.properties")
val releaseSigningProperties = Properties()
val hasReleaseSigning = releaseSigningPropertiesFile.exists()

if (hasReleaseSigning) {
    releaseSigningProperties.load(FileInputStream(releaseSigningPropertiesFile))
}

if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

fun firebaseConfigValue(name: String, fallback: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse(fallback)
        .get()

android {
    namespace = "com.arlessas.gestion"
    compileSdk = 35

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("app/${releaseSigningProperties["storeFile"] as String}")
                storePassword = releaseSigningProperties["storePassword"] as String
                keyAlias = releaseSigningProperties["keyAlias"] as String
                keyPassword = releaseSigningProperties["keyPassword"] as String
            }
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    defaultConfig {
        applicationId = "com.arlessas.gestion.taller"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "4.0.0-taller"
        buildConfigField("boolean", "INCLUDE_TALLER", "true")
        buildConfigField("boolean", "TALLER_STANDALONE", "true")

        if (!googleServicesFile.exists()) {
            resValue("string", "google_app_id", firebaseConfigValue("FIREBASE_GOOGLE_APP_ID", "missing-google-app-id"))
            resValue("string", "google_api_key", firebaseConfigValue("FIREBASE_GOOGLE_API_KEY", "missing-google-api-key"))
            resValue("string", "gcm_defaultSenderId", firebaseConfigValue("FIREBASE_GCM_SENDER_ID", "missing-sender-id"))
            resValue("string", "project_id", firebaseConfigValue("FIREBASE_PROJECT_ID", "missing-project-id"))
            resValue("string", "google_storage_bucket", firebaseConfigValue("FIREBASE_STORAGE_BUCKET", "missing-storage-bucket"))
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/main/java"))
            kotlin.setSrcDirs(listOf("src/main/java"))
            res.setSrcDirs(listOf("src/main/res"))
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))

    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")

    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.code.gson:gson:2.14.0")
}
