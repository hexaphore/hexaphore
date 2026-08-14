plugins {
    id("hexaphore.android.library")
    id("hexaphore.android.hilt")
    // Les DTO sont des data class @Serializable : c'est ce plugin qui leur fabrique
    // un decodeur.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hexaphore.integration.ai"
}

dependencies {
    // Ce module implemente un port declare par le domaine : la fleche remonte.
    api(projects.domain)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
