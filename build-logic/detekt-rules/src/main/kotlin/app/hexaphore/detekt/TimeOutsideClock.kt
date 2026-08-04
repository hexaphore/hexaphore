package app.hexaphore.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * Interdit de lire l'horloge système ailleurs que dans une implémentation de `Clock`.
 *
 * Le filtre porte sur le **nom de fichier** et non sur son chemin : les motifs glob
 * de detekt se comportent différemment selon le séparateur de répertoire, et une
 * règle qui ne se déclenche que sous Linux ne protège rien.
 */
class TimeOutsideClock(config: Config = Config.empty) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "TimeOutsideClock",
            severity = Severity.Defect,
            description = MESSAGE,
            debt = Debt.TWENTY_MINS,
        )

    /**
     * Fichiers autorisés à lire l'horloge système : les implémentations du port `Clock`.
     *
     * Allonger cette liste est une décision qui se discute en revue, pas un moyen de
     * faire taire la règle.
     */
    private val allowedFileNames: List<String> by config(listOf("SystemClock.kt"))

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.containingKtFile.bareFileName() in allowedFileNames) return

        val callee = expression.calleeExpression?.text ?: return
        val receiver =
            (expression.parent as? KtDotQualifiedExpression)
                ?.takeIf { it.selectorExpression === expression }
                ?.receiverExpression
                ?.text

        val readsTheSystemClock =
            when (callee) {
                "currentTimeMillis", "nanoTime" -> true
                "now" -> receiver == null || receiver in TIME_TYPES
                "systemUTC", "systemDefaultZone" -> true
                "getInstance" -> receiver == "Calendar"
                else -> false
            }

        if (readsTheSystemClock) {
            report(CodeSmell(issue, Entity.from(expression), MESSAGE))
        }
    }

    /**
     * Le nom du fichier, sans son chemin.
     *
     * `KtFile.name` vaut tantôt un nom nu, tantôt un chemin absolu selon la façon
     * dont detekt a chargé le fichier. Les deux séparateurs sont coupés : une règle
     * qui ne filtrerait que sous Linux laisserait passer sous Windows exactement ce
     * qu'elle est censée autoriser, et inversement.
     */
    private fun KtFile.bareFileName(): String = name.substringAfterLast('/').substringAfterLast('\\')

    private companion object {
        val TIME_TYPES =
            setOf(
                "Instant",
                "LocalDate",
                "LocalDateTime",
                "LocalTime",
                "OffsetDateTime",
                "OffsetTime",
                "Year",
                "YearMonth",
                "ZonedDateTime",
            )

        const val MESSAGE =
            "Lecture directe de l'horloge systeme. Le temps se lit par le port Clock injecte. " +
                "La moitie de cette application depend du temps (journees, semaines, tendances, " +
                "frontiere de minuit) et un appel direct rend ce code intestable autrement qu'avec " +
                "des Thread.sleep. Rattraper cette decision plus tard, c'est rouvrir chaque fichier. " +
                "Voir docs/06-architecture.md et docs/12-plan-de-developpement.md."
    }
}
