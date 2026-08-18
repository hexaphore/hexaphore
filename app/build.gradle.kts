plugins {
    id("hexaphore.android.application")
    id("hexaphore.android.hilt")
}

android {
    namespace = "app.hexaphore"

    defaultConfig {
        // Verrouille des la premiere publication sur le Play Store : le changer
        // ensuite cree une application entierement nouvelle, sans ses installations
        // ni ses mises a jour. Voir docs/10-qualite-et-livraison.md.
        applicationId = "app.hexaphore"
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

    // AGP 8 ne genere plus BuildConfig sans qu'on le demande. Un seul champ y est lu :
    // VERSION_NAME, que le User-Agent d'Open Food Facts exige (D26). C'est aussi la
    // raison pour laquelle il est lu ici et pas dans le module d'integration -- la
    // version est celle du binaire, suffixe de variante compris.
    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.feature.home)
    implementation(projects.feature.entry)
    implementation(projects.feature.scan)
    implementation(projects.feature.search)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.settings)
    implementation(projects.data.diary)
    implementation(projects.data.food)
    implementation(projects.data.profile)
    implementation(projects.integration.openfoodfacts)
    implementation(projects.integration.ai)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
