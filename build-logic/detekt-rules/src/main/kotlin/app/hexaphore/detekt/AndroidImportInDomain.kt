package app.hexaphore.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Interdit tout import d'une API Android dans `:domain`.
 *
 * Doublon volontaire de la contrainte Gradle : `:domain` n'ayant pas le plugin
 * Android, un tel import ne compile de toute façon pas. Le compilateur dit
 * seulement « unresolved reference ». Cette règle, elle, dit pourquoi — et c'est
 * la seule des deux qui apprenne quelque chose à qui découvre le projet.
 *
 * Activée pour le seul module `:domain`, via `config/detekt/detekt-domain.yml`.
 */
class AndroidImportInDomain(config: Config = Config.empty) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "AndroidImportInDomain",
            severity = Severity.Defect,
            description = MESSAGE,
            debt = Debt.TWENTY_MINS,
        )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val imported = importDirective.importedFqName?.asString() ?: return
        if (FORBIDDEN_PREFIXES.any { imported.startsWith(it) }) {
            report(CodeSmell(issue, Entity.from(importDirective), "$MESSAGE (import : $imported)"))
        }
    }

    private companion object {
        val FORBIDDEN_PREFIXES = listOf("android.", "androidx.", "com.android.", "dalvik.")

        const val MESSAGE =
            "Import d'une API Android dans :domain. Le domaine est du Kotlin pur, et cette purete " +
                "achete trois choses : les regles metier se testent en JVM en quelques millisecondes " +
                "sans emulateur, aucun type Room ni DTO Retrofit ne peut remonter jusqu'au metier, et " +
                "la logique reste portable. Le code qui a besoin d'Android appartient a un adaptateur ; " +
                ":domain n'en connait que le port (une interface). Voir docs/06-architecture.md."
    }
}
