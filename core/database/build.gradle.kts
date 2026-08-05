import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.hexaphore.core.database"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric a besoin des ressources Android pour demarrer une base SQLite
    // sur la JVM. Sans cela, le test de migration exigerait un appareil.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Les schemas exportes deviennent des ressources du test : c'est la que
    // MigrationTestHelper va les chercher pour comparer le schema vivant a celui
    // qui est versionne.
    sourceSets.getByName("test") {
        assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Les schemas sont exportes et versionnes dans git : c'est eux que le test de
    // migration compare, et sans eux une migration ne se verifie plus qu'a l'oeil.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // api : le DAO et ses types font partie de ce que ce module expose a
    // :data:diary, qui en fait la correspondance vers le domaine.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
