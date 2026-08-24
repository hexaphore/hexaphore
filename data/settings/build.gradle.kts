plugins {
    id("hexavore.android.library")
    id("hexavore.android.hilt")
}

android {
    namespace = "app.hexavore.data.settings"

    // Le contrat monte de vraies preferences et un vrai Keystore sous Robolectric :
    // sans cette ligne, il exigerait un appareil, donc ne tournerait pas.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    // Ce module implemente deux ports declares par le domaine : la fleche remonte.
    api(projects.domain)

    implementation(libs.kotlinx.coroutines.core)
    // Pour SharedPreferences.edit {} : trois lignes de moins et un commit qu on ne
    // peut pas oublier.
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    // Robolectric est un lanceur JUnit 4 : le module declare donc junit4 et le moteur
    // vintage, qui rassemble les deux sous ./gradlew check (D35, D53).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
