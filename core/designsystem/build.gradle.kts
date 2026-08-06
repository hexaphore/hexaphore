plugins {
    id("hexaphore.android.library.compose")
}

android {
    namespace = "app.hexaphore.core.designsystem"
}

dependencies {
    // api : le vocabulaire des macros fait partie de la signature des composants.
    // Un module qui affiche une MacroBar doit pouvoir nommer la macro concernee.
    api(projects.domain)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
