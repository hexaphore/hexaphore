package app.hexavore.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * `hexavore.jvm.library` — un module Kotlin/JVM pur : `:domain` et `:core:testing`.
 *
 * L'absence du plugin Android n'est pas une economie, c'est ce qui rend la purete
 * du domaine structurelle : Gradle ne met tout simplement pas les classes
 * `android.*` sur le chemin de compilation, donc personne ne peut les importer par
 * distraction. Voir docs/06-architecture.md.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(catalogVersion("jvmToolchain").toInt())
            }

            configureTestPlatform()
        }
    }
}
