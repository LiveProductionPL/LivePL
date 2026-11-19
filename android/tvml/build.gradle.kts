plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// TVML: serwis informacyjny tvml.pl natywnie, jak TVML na iPhonie (ios/TVML na gałęzi ios).
// Numer budowy jak w LivePL. Klucz podpisu jest osobny (tvml.keystore w %USERPROFILE%\LivePL-klucze\android,
// opis w README.md); AAB do Google Play podpisuje wspólny klucz przesyłania. Na GitHubie oba z sekretów.
val numerBudowy = providers.environmentVariable("LIVEPL_BUDOWA").orNull?.toIntOrNull() ?: 1
val plikKlucza = providers.environmentVariable("TVML_KLUCZ_PLIK").orNull?.takeIf { it.isNotBlank() }
val hasloKlucza = providers.environmentVariable("TVML_KLUCZ_HASLO").orNull
val aliasKlucza = providers.environmentVariable("TVML_KLUCZ_ALIAS").orNull ?: "tvml"

android {
    namespace = "pl.tvml"
    compileSdk = 37

    defaultConfig {
        // nazwa pakietu w Google Play (wybór Karola 19.11.2026; na iPhonie com.LiveProduction.TVML).
        // Po pierwszej wersji w Sklepie nie da się jej zmienić.
        applicationId = "pl.tvml"
        minSdk = 26
        targetSdk = 36
        versionCode = numerBudowy
        // jak na iPhonie i w API (minAppVersion.android); pod ikoną i w Sklepie samo „TVML”
        versionName = "2.0"
    }

    signingConfigs {
        if (plikKlucza != null) {
            create("wydanie") {
                storeFile = file(plikKlucza)
                storePassword = hasloKlucza
                keyAlias = aliasKlucza
                keyPassword = hasloKlucza
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("wydanie")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val compose = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    // strony tvml.pl (formularze, ogłoszenia, regulamin) w karcie przeglądarki, jak Safari na iPhonie
    implementation("androidx.browser:browser:1.8.0")
    // zdjęcia z pamięcią podręczną (WebP z img-webp-v2.php)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
}
