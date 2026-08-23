plugins {
    id("hexaphore.android.library")
    id("hexaphore.android.hilt")
}

android {
    namespace = "app.hexaphore.core.common"
}

dependencies {
    // api : ce module implemente des ports declares par le domaine, et ses
    // liaisons Hilt exposent ces types-la.
    api(projects.domain)

    implementation(libs.kotlinx.coroutines.core)

    // Le jour regarde a deux implementations -- celle-ci et le faux de :core:testing --
    // et D53 veut qu'un seul jeu de cas les eprouve toutes les deux.
    testImplementation(projects.core.testing)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
