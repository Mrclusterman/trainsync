// Apps nativas sin servidor (docs/VISION.md §7): núcleo compartido en Kotlin Multiplatform, Android en Compose, iOS en SwiftUI
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
