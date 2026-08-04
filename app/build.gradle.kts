import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.hexaphore"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        // Verrouille des la premiere publication sur le Play Store : le changer
        // ensuite cree une application entierement nouvelle, sans ses installations
        // ni ses mises a jour. Voir docs/10-qualite-et-livraison.md.
        applicationId = "app.hexaphore"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode =
            libs.versions.versionCode
                .get()
                .toInt()
        versionName = libs.versions.versionName.get()
    }

    buildTypes {
        debug {
            // Suffixe pour que la version de developpement cohabite avec celle
            // installee depuis une release.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
