plugins {
    id("hexaphore.android.library")
    id("hexaphore.android.hilt")
}

android {
    namespace = "app.hexaphore.data.food"
}

dependencies {
    // Ce module implemente cinq ports declares par le domaine : la fleche remonte,
    // :data depend de :domain et jamais l'inverse.
    api(projects.domain)
    implementation(projects.core.database)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
