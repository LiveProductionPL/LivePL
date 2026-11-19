// AGP 9 ma wbudowany Kotlin; wtyczka kotlin.android z „apply false” tylko wybiera jego wersję.
plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
