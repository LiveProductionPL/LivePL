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

// Powiadomienia push przez Firebase Cloud Messaging (projekt tvml-3f2b5, bez Analytics).
// google-services.json jest poza gitem (repo jest publiczne): lokalnie leży obok tego pliku, na GitHubie przychodzi
// z sekretu TVML_FIREBASE (wpisuje go Karol: narzedzia/ustaw-sekrety.mjs --firebase <plik>). Zamiast wtyczki
// com.google.gms.google-services te same zasoby tworzy resValue niżej, więc działa też wersja testowa pl.tvml.debug,
// a bez pliku aplikacja buduje się normalnie, tylko bez powiadomień (Powiadomienia.kt to sprawdza).
val firebase: Map<String, String> = run {
    val tekst = file("google-services.json").takeIf { it.isFile }?.readText()
        ?: providers.environmentVariable("TVML_FIREBASE").orNull?.takeIf { it.isNotBlank() }
        ?: return@run emptyMap()
    @Suppress("UNCHECKED_CAST")
    val j = groovy.json.JsonSlurper().parseText(tekst) as Map<String, Any?>
    val projekt = j["project_info"] as Map<String, Any?>
    val klient = (j["client"] as List<Map<String, Any?>>).first {
        ((it["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>)["package_name"] == "pl.tvml"
    }
    mapOf(
        "google_app_id" to (klient["client_info"] as Map<String, Any?>)["mobilesdk_app_id"].toString(),
        "google_api_key" to ((klient["api_key"] as List<Map<String, Any?>>).first()["current_key"]).toString(),
        "gcm_defaultSenderId" to projekt["project_number"].toString(),
        "project_id" to projekt["project_id"].toString(),
    )
}

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
        versionName = "2.1"
        // zasoby, z których FirebaseInitProvider sam uruchamia Firebase (jak z wtyczki google-services)
        firebase.forEach { (nazwa, wartosc) -> resValue("string", nazwa, wartosc) }
        buildConfigField("boolean", "PUSH", firebase.isNotEmpty().toString())
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
        // zasoby Firebase z google-services.json (resValue w defaultConfig)
        resValues = true
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
    // sceny pogody SVG motywów ze Studia motywów (config.theme.weatherSceneSvgUrl)
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
    // powiadomienia push: samo Firebase Cloud Messaging, bez firebase-analytics
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-messaging")
}
