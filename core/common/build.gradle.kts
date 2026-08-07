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
}
