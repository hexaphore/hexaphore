package app.hexavore.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `hexavore.android.application` — l'application.
 *
 * Ce qui reste dans `app/build.gradle.kts` est ce qui appartient a l'application et
 * a elle seule : son `applicationId`, ses types de build, sa version. Une convention
 * n'a aucune raison de porter l'identite d'un module unique — elle porte ce qui se
 * repete.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                buildFeatures.compose = true
            }

            configureTestPlatform()
        }
    }
}
