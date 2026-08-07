plugins {
    // Kotlin/JVM comme :domain : les fausses implementations n'ont aucune raison
    // d'avoir besoin d'Android, et le verifier ici coute zero.
    id("hexaphore.jvm.library")
}

dependencies {
    // api : ce module expose des types du domaine dans ses signatures.
    api(projects.domain)
    implementation(libs.kotlinx.coroutines.core)
}
