# Kod LivePL nie używa refleksji ani @JavascriptInterface; R8 może skracać wszystko.
# Numery linii zostają, żeby zgłoszenia błędów dało się odczytać.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit (tłumacz stron w LivePL) tworzy swoje rejestratory przez refleksję: nazwy klas są w manifeście,
# a obiekt powstaje z konstruktora bez argumentów. R8 w trybie pełnym (domyślnym od AGP 9) usuwa takie
# konstruktory i wtedy tłumacz wywraca aplikację już przy rozpoznawaniu języka strony. Tak samo ML Kit
# wybiera sposób wysyłki danych diagnostycznych (datatransport).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class * implements com.google.android.datatransport.runtime.backends.BackendFactory { <init>(); }
