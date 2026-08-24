package app.hexavore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * Ce qu'on voit en arrivant : son profil, son objectif, ses six compteurs — **en
 * lecture**.
 *
 * Aucun champ, aucune bordure, aucun bouton par ligne ([D60][decisions]). Un écran de
 * réglages dont tout est saisissable en permanence invite à corriger ce qu'on venait
 * seulement relire, et ne laisse aucun moment où l'on puisse simplement vérifier un
 * chiffre. Le crayon, en tête d'écran, est la seule porte.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun ProfileSummary(state: ProfileUiState) {
    val form = state.form

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionTitle(stringResource(R.string.profile_you_title))
            ReadLine(line(R.string.profile_label_birth, form.birthDate?.formatLong()))
            ReadLine(line(R.string.profile_label_sex, form.sex?.let { stringResource(it.labelRes) }))
            ReadLine(line(R.string.profile_label_height, form.heightCm?.let { centimetres(it) }))
            ReadLine(line(R.string.profile_label_weight, form.currentWeightKg?.let { kilogrammes(it) }))
            ReadLine(line(R.string.profile_label_activity, form.activityLevel?.let { stringResource(it.labelRes) }))
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionTitle(stringResource(R.string.profile_objective_title))
            ReadLine(line(R.string.profile_label_strategy, form.strategy?.let { stringResource(it.labelRes) }))
            // Le maintien n'a ni cible ni echeance : afficher deux lignes « non
            // renseigne » ferait passer pour un oubli ce qui est la reponse exacte.
            if (form.strategy != null && form.strategy != GoalStrategy.MAINTAIN) {
                ReadLine(line(R.string.profile_label_target, form.targetWeightKg?.let { kilogrammes(it) }))
                ReadLine(line(R.string.profile_label_horizon, form.targetDate?.formatLong()))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionTitle(stringResource(R.string.profile_counters_title))
            // D'ou viennent ces six chiffres est une information a part entiere : elle
            // dit si corriger sa taille les deplacera.
            Body(
                stringResource(
                    if (form.manual) R.string.profile_mode_manual else R.string.profile_mode_calculated,
                ),
            )
            CounterLines(state)
        }
    }
}

@Composable
private fun CounterLines(state: ProfileUiState) {
    val daily = state.daily
    if (daily == null) {
        Body(stringResource(R.string.profile_counters_unavailable))
        return
    }
    Macro.entries.forEach { macro ->
        ReadLine(
            stringResource(
                R.string.profile_counter_line,
                stringResource(macro.labelRes),
                daily[macro].roundToInt(),
                stringResource(macro.unitRes),
            ),
        )
    }
}

/** « Taille : 182 cm », ou « Taille : non renseigné » — jamais une ligne vide. */
@Composable
private fun line(labelRes: Int, value: String?): String = stringResource(
    R.string.profile_line,
    stringResource(labelRes),
    value ?: stringResource(R.string.profile_value_unknown),
)

@Composable
private fun centimetres(value: Double): String = stringResource(R.string.profile_value_cm, formatDecimal(value))

@Composable
private fun kilogrammes(value: Double): String = stringResource(R.string.profile_value_kg, formatDecimal(value))
