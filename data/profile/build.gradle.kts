plugins {
    id("hexaphore.android.library")
    id("hexaphore.android.hilt")
}

android {
    namespace = "app.hexaphore.data.profile"

    // Robolectric a besoin des ressources Android pour ouvrir une base SQLite sur la
    // JVM. Sans cela, le contrat des trois ports exigerait un appareil.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Ce module implemente trois ports declares par le domaine : la fleche remonte,
    // :data depend de :domain et jamais l'inverse.
    api(projects.domain)
    implementation(projects.core.database)

    implementation(libs.kotlinx.coroutines.core)

    // JUnit 4 et le moteur vintage pour la meme raison qu'en :core:database (D35) :
    // Robolectric est un lanceur JUnit 4, et le contrat se joue sur la vraie base.
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
