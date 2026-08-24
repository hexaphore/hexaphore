package app.hexavore.gradle

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `hexavore.android.library` — une bibliotheque Android du projet.
 *
 * Le module qui l'applique ne declare plus que son `namespace` et ses dependances.
 * Tout le reste — palier de compilation, minimum supporte, niveau de bytecode,
 * plateforme de test — vient d'ici, en un seul exemplaire.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
            }

            configureTestPlatform()
        }
    }
}
