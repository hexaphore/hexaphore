plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
    // Les DTO sont des data class @Serializable, comme les routes de navigation :
    // c'est ce plugin qui leur fabrique un decodeur.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.hexavore.integration.openfoodfacts"
}

dependencies {
    // Ce module implemente un port declare par le domaine : la fleche remonte.
    api(projects.domain)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
    // Un vrai serveur sur la boucle locale, pas un faux client : c'est le seul
    // montage qui eprouve l'en-tete pose par l'intercepteur et le retrait
    // exponentiel, qui vivent tous les deux sous l'API de Retrofit.
    //
    // JUnit 4 vient avec lui, et non pour ecrire des tests : MockWebServer herite
    // d'ExternalResource, qui est une regle JUnit 4. Sans cette ligne, la classe
    // manque a l'execution -- pas a la compilation. Aucun moteur vintage ici, les
    // cas sont bien du JUnit 5.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.platform.launcher)
}
