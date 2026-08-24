package app.hexavore.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * Interdit d'écrire une couleur ailleurs que dans `:core:designsystem`.
 *
 * Cette règle est désactivée pour le seul module qui a le droit d'en définir, via
 * `config/detekt/detekt-designsystem.yml`. Elle est volontairement grossière : elle
 * ne fait aucune résolution de types et se contente du nom `Color`. Un faux positif
 * se corrige en une ligne, un faux négatif se découvre en changeant de thème.
 */
class HardcodedColor(config: Config = Config.empty) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "HardcodedColor",
            severity = Severity.Maintainability,
            description = MESSAGE,
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text == COLOR) {
            report(CodeSmell(issue, Entity.from(expression), MESSAGE))
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.receiverExpression.text == COLOR) {
            report(CodeSmell(issue, Entity.from(expression), MESSAGE))
        }
    }

    private companion object {
        const val COLOR = "Color"

        const val MESSAGE =
            "Couleur ecrite en dur. Les teintes du projet vivent dans :core:designsystem " +
                "(MacroColors.kt, NeonTheme.kt) et nulle part ailleurs. Une couleur ecrite ici " +
                "ne suivra pas le theme clair, ne sera pas desaturee dans l'etat non atteint, et " +
                "ne bougera pas le jour ou la palette change : la regle ajoutee apres 5 000 lignes " +
                "ne s'applique pas retroactivement. Utiliser NeonTheme.macros[...] " +
                "ou MaterialTheme.colorScheme. Voir docs/08-design-system.md."
    }
}
