package app.hexaphore.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `hexaphore.android.hilt` — l'injection de dependances.
 *
 * Les quatre elements voyagent ensemble et n'ont aucun sens separement : le plugin
 * Hilt sans KSP ne genere rien, la bibliotheque sans le compilateur non plus. Un
 * module qui en oubliait un compilait quand meme et echouait a l'execution — le
 * pire des deux mondes, puisque rien ne le signalait avant l'appareil.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                "implementation"(catalogLibrary("hilt-android"))
                "ksp"(catalogLibrary("hilt-compiler"))
            }
        }
    }
}
