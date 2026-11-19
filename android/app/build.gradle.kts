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

// Tylko do testów budowy debug: -Plivepl.adresUrzedu=http://localhost:8766 (próbny urząd zamiast cert.livepl.eu).
val adresUrzeduTestowy = providers.gradleProperty("livepl.adresUrzedu").orNull

android {
    namespace = "eu.livepl.przegladarka"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = numerBudowy
        versionName = "0.1.0"
        if (tylkoTelefony) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        buildConfigField("String", "ADRES_URZEDU", "\"https://cert.livepl.eu\"")
        buildConfigField("boolean", "URZAD_TESTOWY", "false")
    }

    // Dwie aplikacje z tego samego kodu: przeglądarka LivePL i Poczta LivePL (poczta.livepl.eu na cały ekran).
    flavorDimensions += "aplikacja"
    productFlavors {
        create("przegladarka") {
            dimension = "aplikacja"
            applicationId = "eu.livepl.przegladarka"
            buildConfigField("boolean", "POCZTA", "false")
        }
        create("poczta") {
            dimension = "aplikacja"
            applicationId = "eu.livepl.poczta"
            buildConfigField("boolean", "POCZTA", "true")
        }
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
            if (adresUrzeduTestowy != null) {
                buildConfigField("String", "ADRES_URZEDU", "\"$adresUrzeduTestowy\"")
                buildConfigField("boolean", "URZAD_TESTOWY", "true")
            }
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

/**
 * Wspólne pliki z LivePL na Windows i iPhonie trafiają do assets: ../programy.json oraz certyfikaty
 * urzędu z ../assets/ca (root i pośrednie), którymi aplikacja sprawdza swój certyfikat „Urządzenie LivePL”.
 */
abstract class KopiujZasoby : DefaultTask() {
    @get:InputFile
    abstract val programy: RegularFileProperty

    @get:InputDirectory
    abstract val katalogCA: DirectoryProperty

    @get:Optional
    @get:InputFile
    abstract val testowyRoot: RegularFileProperty

    @get:Input
    abstract val testoweDomeny: ListProperty<String>

    @get:OutputDirectory
    abstract val wyjscie: DirectoryProperty

    @TaskAction
    fun kopiuj() {
        val cel = wyjscie.get().asFile
        cel.deleteRecursively()
        cel.mkdirs()
        programy.get().asFile.copyTo(cel.resolve("programy.json"), overwrite = true)
        val ca = katalogCA.get().asFile
        val certyfikaty = { katalog: File -> katalog.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("crt", "pem") } ?: emptyArray() }
        certyfikaty(ca).forEach { it.copyTo(cel.resolve("ca/${it.name}"), overwrite = true) }
        certyfikaty(ca.resolve("posrednie")).forEach { it.copyTo(cel.resolve("ca/posrednie/${it.name}"), overwrite = true) }
        if (testoweDomeny.get().isEmpty()) {
            ca.resolve("domeny.json").copyTo(cel.resolve("ca/domeny.json"), overwrite = true)
        } else {
            @Suppress("UNCHECKED_CAST")
            val domeny = ((groovy.json.JsonSlurper().parse(ca.resolve("domeny.json")) as Map<String, Any?>)["domeny"] as? List<String>).orEmpty()
            val wszystkie = (domeny + testoweDomeny.get()).joinToString(", ") { "\"${it.trim()}\"" }
            cel.resolve("ca/domeny.json").writeText("{ \"domeny\": [$wszystkie] }\n")
        }
        testowyRoot.orNull?.asFile?.copyTo(cel.resolve("ca/testowy-root.crt"), overwrite = true)
    }
}

/**
 * Zaufanie do urzędu certyfikacji LivePL, jak src/nasze-ca.js na Windows i NaszeCA.swift na iPhonie:
 * roota z ../assets/ca (*.crt) Android przyjmuje tylko dla domen z ../assets/ca/domeny.json,
 * a wszędzie indziej obowiązują wyłącznie certyfikaty systemowe. Powstaje res/xml/siec.xml
 * (network security config, którego używa też WebView) i res/raw z certyfikatami.
 * Do testów: -Plivepl.testowyRoot=<plik .crt> i -Plivepl.testoweDomeny=a.pl,b.pl (tylko budowa debug).
 */
abstract class ZaufanieLivePL : DefaultTask() {
    @get:InputDirectory
    abstract val katalogCA: DirectoryProperty

    @get:Optional
    @get:InputFile
    abstract val testowyRoot: RegularFileProperty

    @get:Input
    abstract val testoweDomeny: ListProperty<String>

    @get:OutputDirectory
    abstract val wyjscie: DirectoryProperty

    @TaskAction
    fun zrob() {
        val ca = katalogCA.get().asFile
        val cel = wyjscie.get().asFile
        cel.deleteRecursively()
        val raw = cel.resolve("raw").apply { mkdirs() }
        val xml = cel.resolve("xml").apply { mkdirs() }

        val rooty = (ca.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("crt", "pem") }?.sortedBy { it.name }
            ?: emptyList()) + listOfNotNull(testowyRoot.orNull?.asFile)
        val zasoby = rooty.mapIndexed { i, plik ->
            val nazwa = "livepl_ca_$i"
            plik.copyTo(raw.resolve("$nazwa.pem"), overwrite = true)
            nazwa
        }
        @Suppress("UNCHECKED_CAST")
        val zPliku = (groovy.json.JsonSlurper().parse(ca.resolve("domeny.json")) as Map<String, Any?>)["domeny"] as? List<String>
        val domeny = ((zPliku ?: emptyList()) + testoweDomeny.get())
            .map { it.trim().lowercase() }
            .filter { Regex("""^[a-z0-9.-]+$""").matches(it) }
            .distinct()

        val wpisyDomen = domeny.joinToString("\n") { "        <domain includeSubdomains=\"true\">$it</domain>" }
        val wpisyCA = zasoby.joinToString("\n") { "            <certificates src=\"@raw/$it\" />" }
        xml.resolve("siec.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!-- Plik tworzy zadanie zaufanieLivePL w app/build.gradle.kts z ../assets/ca. Nie edytuj ręcznie. -->
            |<network-security-config>
            |    <!-- przeglądarka otwiera też zwykłe http (z ostrzeżeniem przy adresie) -->
            |    <base-config cleartextTrafficPermitted="true">
            |        <trust-anchors>
            |            <certificates src="system" />
            |        </trust-anchors>
            |    </base-config>
            |    <!-- nasze domeny: certyfikaty systemowe (np. Let's Encrypt) i urząd LivePL -->
            |    <domain-config cleartextTrafficPermitted="true">
            |$wpisyDomen
            |        <trust-anchors>
            |            <certificates src="system" />
            |$wpisyCA
            |        </trust-anchors>
            |    </domain-config>
            |</network-security-config>
            |""".trimMargin(),
        )
    }
}

androidComponents {
    onVariants { wariant ->
        val nazwa = wariant.name.replaceFirstChar { it.uppercase() }
        val testy = wariant.buildType == "debug"
        val testowyRoot = if (testy) providers.gradleProperty("livepl.testowyRoot").orNull else null

        val zasoby = tasks.register<KopiujZasoby>("kopiujZasoby$nazwa") {
            programy.set(rootProject.layout.projectDirectory.file("../programy.json"))
            katalogCA.set(rootProject.layout.projectDirectory.dir("../assets/ca"))
            testowyRoot?.let { this.testowyRoot.set(file(it)) }
            testoweDomeny.set(
                if (testy) providers.gradleProperty("livepl.testoweDomeny").orNull?.split(',') ?: emptyList() else emptyList(),
            )
        }
        wariant.sources.assets?.addGeneratedSourceDirectory(zasoby, KopiujZasoby::wyjscie)

        val zaufanie = tasks.register<ZaufanieLivePL>("zaufanieLivePL$nazwa") {
            katalogCA.set(rootProject.layout.projectDirectory.dir("../assets/ca"))
            testowyRoot?.let { this.testowyRoot.set(file(it)) }
            testoweDomeny.set(
                if (testy) providers.gradleProperty("livepl.testoweDomeny").orNull?.split(',') ?: emptyList() else emptyList(),
            )
        }
        wariant.sources.res?.addGeneratedSourceDirectory(zaufanie, ZaufanieLivePL::wyjscie)
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
