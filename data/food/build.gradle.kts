plugins {
    id("hexaphore.android.library")
    id("hexaphore.android.hilt")
}

android {
    namespace = "app.hexaphore.data.food"

    // Robolectric a besoin des ressources Android -- et des assets, dont ciqual.db
    // que :core:database livre -- pour ouvrir une base SQLite sur la JVM. Sans cela,
    // le jeu de tests de contrat exigerait un appareil, donc ne tournerait pas.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Ce module implemente cinq ports declares par le domaine : la fleche remonte,
    // :data depend de :domain et jamais l'inverse.
    api(projects.domain)
    implementation(projects.core.database)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    // JUnit 4 et le moteur vintage pour la meme raison qu'en :core:database (D35) :
    // Robolectric est un lanceur JUnit 4, et le contrat se joue sur la vraie base.
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
}
