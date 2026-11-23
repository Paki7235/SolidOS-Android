plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numero di versione: lo dà la build su GitHub (una per ogni pubblicazione); in locale 1
val numeroVersione = (System.getenv("SOLIDOS_VERSIONE") ?: "1").toInt()

android {
    namespace = "it.solidos.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.solidos.home"
        minSdk = 29
        targetSdk = 35
        versionCode = numeroVersione
        versionName = System.getenv("SOLIDOS_NOME_VERSIONE") ?: "sviluppo"
        buildConfigField("String", "AGGIORNAMENTI", "\"https://github.com/Paki7235/SolidOS-Android/releases/latest/download/versione.json\"")
    }

    // Firma: la chiave di SolidOS (segreta, su GitHub). Android installa un aggiornamento solo
    // se è firmato con la stessa chiave della versione installata.
    signingConfigs {
        create("solidos") {
            val file = System.getenv("SOLIDOS_KEYSTORE")
            if (file != null) {
                storeFile = file(file)
                storePassword = System.getenv("SOLIDOS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SOLIDOS_KEY_ALIAS") ?: "solidos"
                keyPassword = System.getenv("SOLIDOS_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("SOLIDOS_KEYSTORE") != null) signingConfigs.getByName("solidos") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
