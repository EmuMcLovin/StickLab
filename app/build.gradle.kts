plugins {
    alias(libs.plugins.android.application)
    // Zusatz für den Kotlin-Übersetzer, damit er Compose-Code versteht (Entscheidung U-1)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sticklab.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sticklab.app"
        minSdk = 30
        targetSdk = 37
        // 1 = v1.0 auf GitHub. Bei jeder veröffentlichten Version erhöhen, sonst lässt Android das Update nicht zu
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Debug-Version läuft als eigene App neben der installierten Version 1.0
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    // Phase 2 / E, 6e: sticklab_mirror liegt als libslmirror.so je Variante in app/src/debug|release/jniLibs (eigene Fassung).
    // Beim Installieren auspacken, damit es als ausführbare Datei im nativeLibraryDir liegt,
    // und nicht verändern (kein Strip), sonst stimmt die Prüfsumme in MirrorControl nicht
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libslmirror.so"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Neue Oberfläche mit Jetpack Compose (Entscheidung 09.10.2026, Weg B 15.10.2026)
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Compose (Entscheidung U-1): Stückliste, Bausteine (Reiter, Karten, Regler, Dialoge),
    // Verbindung zum Bildschirm, Vorschau in Android Studio (Werkzeug dafür nur in der Debug-Version)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}