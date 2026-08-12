plugins {
    id("hexaphore.android.feature")
}

android {
    namespace = "app.hexaphore.feature.scan"
}

dependencies {
    // La seule dependance d'un :feature vers un :integration, et elle est declaree
    // ici pour se faire remarquer. Une camera est une **surface** et non une source
    // de donnees : l'abstraire derriere un port demanderait au domaine de connaitre
    // un type de vue, ce que l'architecture lui interdit (D65).
    implementation(projects.integration.scanner)

    // La demande de permission a l'execution, et sa lecture.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
