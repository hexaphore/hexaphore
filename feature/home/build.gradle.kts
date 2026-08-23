plugins {
    id("hexaphore.android.feature")
}

android {
    namespace = "app.hexaphore.feature.home"
}

dependencies {
    // Le calendrier de l'accueil. Ses deux composables sont batis sur LazyRow et
    // LazyColumn : seules les cellules visibles existent, ce qui est exactement ce
    // qu'on lui demande. Licence MIT, et il n'entre que dans ce module.
    implementation(libs.calendar.compose)

    // `BackHandler` : le bouton retour du systeme ramene a aujourd'hui quand un jour
    // passe est affiche. Sans lui, il quitterait l'application depuis l'historique.
    implementation(libs.androidx.activity.compose)
}
