package app.hexaphore.gradle

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `hexaphore.android.library.compose` — une bibliotheque Android qui emet de
 * l'interface.
 *
 * Compose est separe de [AndroidLibraryConventionPlugin] parce que trois modules
 * sur cinq n'en ont pas besoin, et qu'activer `buildFeatures.compose` allume un
 * traitement d'annotations qu'ils paieraient pour rien.
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("hexaphore.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }
        }
    }
}
