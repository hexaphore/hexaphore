plugins {
    id("hexavore.android.feature")
}

android {
    namespace = "app.hexavore.feature.capture"
}

dependencies {
    // Le declencheur photo et le selecteur de galerie sont deux contrats de resultat
    // d'activite : c'est le systeme qui prend la photo, pas nous.
    implementation(libs.androidx.activity.compose)

    // FileProvider : depuis Android 7, remettre un chemin brut a une autre
    // application leve FileUriExposedException.
    implementation(libs.androidx.core.ktx)
}
