plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// Coordonnee substituee par le build composite : c'est elle que le build principal
// declare dans sa configuration detektPlugins.
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
    // compileOnly : detekt fournit son API a l'execution. La packager ici
    // produirait deux copies de l'API sur le classpath de l'analyse.
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)

    // compileContentForTest : la seule facon de fabriquer un fichier portant un nom
    // choisi, donc d'eprouver le filtre par nom de fichier de TimeOutsideClock.
    testImplementation(libs.detekt.test.utils)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Piege connu, verifie sur ce projet : apres avoir modifie une regle, le demon
// Gradle peut continuer a executer l'ancienne. detekt tourne dans un worker dont
// le chargeur de classes est mis en cache par le demon, et le chemin du jar ne
// change pas d'un build a l'autre. Les tests de ce module, eux, voient toujours
// le code a jour -- c'est le comportement de detekt lui-meme qui retarde.
//
//     gradlew --stop
//
// La CI n'est pas concernee : elle demarre sur un demon neuf a chaque execution.
