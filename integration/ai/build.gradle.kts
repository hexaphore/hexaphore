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
    // La meme pile que pour Open Food Facts, et c'est l'argument de D73 : six
    // fournisseurs derriere un seul client, donc un seul intercepteur de redaction.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
    // Un vrai serveur sur la boucle locale, pas un faux client : c'est le seul
    // montage qui eprouve ce qui vit sous l'API de Retrofit -- l'intercepteur de
    // redaction, les en-tetes, et le corps reellement serialise.
    //
    // JUnit 4 vient avec lui et non pour ecrire des tests : MockWebServer herite
    // d'ExternalResource, qui est une regle JUnit 4. Sans cette ligne, la classe
    // manque a l'execution -- pas a la compilation.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.platform.launcher)
}
