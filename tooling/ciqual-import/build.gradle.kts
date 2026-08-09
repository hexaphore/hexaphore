import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    // Kotlin/JVM pur : ce code tourne sur la machine de developpement, jamais sur
    // un telephone. Il n'entre dans aucun APK.
    id("hexaphore.jvm.library")
}

dependencies {
    // Pour SearchText, et pour elle seule. La normalisation doit etre exactement la
    // meme a l'ecriture de l'index et a la lecture d'une saisie : deux copies d'une
    // meme regle divergent le jour ou l'une apprend les ligatures et l'autre non.
    implementation(projects.domain)

    // Le pilote embarque sa propre compilation de SQLite. C'est elle qui ecrit la
    // base ; celle d'Android ne fait que la lire.
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// --- La tache d'import -------------------------------------------------------
//
// Elle n'est branchee sur aucun cycle de vie de Gradle. La base generee est
// versionnee -- sans elle, un clone suivi d'un build donnerait une application sans
// catalogue -- et la regenerer a chaque compilation produirait un binaire de
// plusieurs megaoctets modifie dans chaque diff. On la lance quand l'ANSES publie.
//
// Les chemins sont resolus ici, en configuration, et passes en chaines. Une lambda
// qui les resoudrait a l'execution capturerait l'objet du script, que le cache de
// configuration ne sait pas serialiser.

val sourceArchive = layout.projectDirectory.file("../ciqual/ciqual-2025-11-03-xml.zip").asFile
val servingsTable = layout.projectDirectory.file("../ciqual/servings.csv").asFile
val sourceChecksums = layout.projectDirectory.file("../ciqual/SOURCE.sha256").asFile
val generatedDatabase = rootProject.layout.projectDirectory.file("core/database/src/main/assets/ciqual.db").asFile

tasks.register<JavaExec>("importCiqual") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Convertit le XML de l'ANSES en core/database/src/main/assets/ciqual.db."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.hexaphore.tooling.ciqual.CiqualImportKt")

    inputs.files(sourceArchive, servingsTable, sourceChecksums)
    outputs.file(generatedDatabase)

    args(
        sourceArchive.absolutePath,
        servingsTable.absolutePath,
        sourceChecksums.absolutePath,
        generatedDatabase.absolutePath,
    )
}
