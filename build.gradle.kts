import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Configuration de l'analyse statique posee ici plutot que dupliquee dans chaque
// module. Trois modules ne justifient pas encore des plugins de convention : ce
// serait une couche d'indirection pour dix lignes. Elle deviendra necessaire vers
// le sixieme module, et c'est a ce moment-la qu'il faudra la creer.
subprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        // Le premier fichier porte les reglages communs ; le second, quand il
        // existe, n'active ou ne desactive que ce qui est propre au module.
        // Un filtre par chemin (includes / excludes) aurait fait la meme chose
        // en apparence, mais les motifs glob de detekt se comportent mal sur
        // les separateurs Windows.
        config.setFrom(rootProject.files("config/detekt/detekt.yml") + moduleDetektConfig())
    }

    extensions.configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.get())
        // Les rapports de ktlint dans le repertoire de build suffisent ; on ne
        // veut pas d'un formatage automatique silencieux pendant un `check`.
        outputToConsole.set(true)
    }

    dependencies {
        add("detektPlugins", rootProject.libs.hexaphore.detekt.rules)
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = rootProject.libs.versions.jvmToolchain.get()
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            md.required.set(false)
            txt.required.set(false)
        }
    }
}

/**
 * Reglages detekt propres a un module, le cas echeant.
 *
 * `:domain` interdit les imports Android ; `:core:designsystem` est le seul
 * endroit ou une couleur a le droit d'etre ecrite.
 */
fun Project.moduleDetektConfig(): ConfigurableFileCollection {
    val overlay =
        when (path) {
            ":domain" -> "config/detekt/detekt-domain.yml"
            ":core:designsystem" -> "config/detekt/detekt-designsystem.yml"
            else -> null
        }
    return rootProject.files(listOfNotNull(overlay))
}

// Les regles detekt maison vivent dans un build inclus : sans ce lien explicite,
// leurs tests ne tourneraient jamais. Une regle non testee est une regle qu'on
// croit active.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":detekt-rules:check"))
}
