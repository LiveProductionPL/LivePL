plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numer budowy: na GitHubie numer przebiegu workflow, lokalnie 1.
val numerBudowy = providers.environmentVariable("LIVEPL_BUDOWA").orNull?.toIntOrNull() ?: 1

// Klucz podpisu wydań. Nie leży w repozytorium: na GitHubie przychodzi z sekretów,
// lokalnie z %USERPROFILE%\LivePL-klucze\android (opis w README.md).
val plikKlucza = providers.environmentVariable("LIVEPL_KLUCZ_PLIK").orNull?.takeIf { it.isNotBlank() }
val hasloKlucza = providers.environmentVariable("LIVEPL_KLUCZ_HASLO").orNull
val aliasKlucza = providers.environmentVariable("LIVEPL_KLUCZ_ALIAS").orNull ?: "livepl"

// -Plivepl.tylkoTelefony: APK na app.livepl.eu tylko dla procesorów ARM telefonów (tłumacz ML Kit
// waży ok. 7 MB na każdą architekturę). AAB do Google Play ma wszystkie, a Sklep sam wybiera właściwą.
val tylkoTelefony = providers.gradleProperty("livepl.tylkoTelefony").isPresent

android {
    namespace = "eu.livepl.przegladarka"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.livepl.przegladarka"
        minSdk = 26
        targetSdk = 36
        versionCode = numerBudowy
        versionName = "0.1.0"
        if (tylkoTelefony) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
        // biblioteki tłumacza spakowane: mniejszy APK do pobrania ze strony
        jniLibs.useLegacyPackaging = true
    }
}

// Ta sama lista programów co w LivePL na Windows i iPhonie: ../programy.json trafia do assets.
abstract class KopiujProgramy : DefaultTask() {
    @get:InputFile
    abstract val zrodlo: RegularFileProperty

    @get:OutputDirectory
    abstract val wyjscie: DirectoryProperty

    @TaskAction
    fun kopiuj() {
        val cel = wyjscie.get().asFile
        cel.mkdirs()
        zrodlo.get().asFile.copyTo(cel.resolve("programy.json"), overwrite = true)
    }
}

val kopiujProgramy = tasks.register<KopiujProgramy>("kopiujProgramy") {
    zrodlo.set(rootProject.layout.projectDirectory.file("../programy.json"))
}

androidComponents {
    onVariants { wariant ->
        wariant.sources.assets?.addGeneratedSourceDirectory(kopiujProgramy, KopiujProgramy::wyjscie)
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
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    // tłumaczenie stron na telefonie: tekst nie wychodzi z urządzenia, słowniki pobierają się raz
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
}
