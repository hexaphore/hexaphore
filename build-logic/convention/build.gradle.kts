plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "app.hexaphore.buildlogic"
version = libs.versions.buildLogic.get()

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

dependencies {
    // `compileOnly` et non `implementation`, et ce n'est pas un detail de gout.
    //
    // Ces plugins sont deja sur le chemin de classes du build racine, qui les
    // declare en `apply false`. Les embarquer ici aussi en produirait un second
    // exemplaire dans un chargeur de classes fils : deux `BaseExtension` distincts
    // pour Gradle, et des erreurs de type sans rapport apparent avec la cause.
    // En compileOnly, les conventions compilent contre l'API et la retrouvent a
    // l'execution dans le chargeur parent -- un seul exemplaire, partage avec
    // detekt et ktlint.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

// Des classes et non des scripts precompiles (`hexaphore.android.library.gradle.kts`).
// Un script precompile resout les plugins de son propre bloc `plugins { }` sur son
// chemin de classes d'execution, ce qui obligerait a embarquer AGP ici en
// `implementation` -- c'est-a-dire a en avoir un second exemplaire. Une classe
// applique par identifiant, resolu sur le chemin du module cible : un seul AGP,
// celui du build racine.
gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "hexaphore.jvm.library"
            implementationClass = "app.hexaphore.gradle.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "hexaphore.android.library"
            implementationClass = "app.hexaphore.gradle.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "hexaphore.android.library.compose"
            implementationClass = "app.hexaphore.gradle.AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "hexaphore.android.application"
            implementationClass = "app.hexaphore.gradle.AndroidApplicationConventionPlugin"
        }
        register("androidHilt") {
            id = "hexaphore.android.hilt"
            implementationClass = "app.hexaphore.gradle.HiltConventionPlugin"
        }
        register("androidFeature") {
            id = "hexaphore.android.feature"
            implementationClass = "app.hexaphore.gradle.AndroidFeatureConventionPlugin"
        }
    }
}
