package app.hexaphore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexaphore.core.designsystem.component.DraftTextField
import app.hexaphore.core.designsystem.component.NeonChip
import app.hexaphore.core.designsystem.component.isWholeNumberField
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.nutrition.Macro
import kotlin.math.roundToInt

/**
 * **Vos six compteurs.** Ce que le calcul propose, et ce que vous avez fixé.
 *
 * Un compteur fixé à la main survit à tous les recalculs qui suivront : corriger sa
 * taille ou son échéance déplace les cinq autres et laisse celui-là où il est
 * ([app.hexaphore.domain.goal.DailyGoal.overriddenBy]). C'est la promesse que
 * [docs/02][parcours] fait — « un objectif édité à la main est marqué comme tel et
 * n'est plus écrasé par un recalcul sans confirmation explicite ».
 *
 * Rien tant que le formulaire est incomplet : six chiffres dérivés d'un profil à
 * moitié rempli seraient l'objectif de quelqu'un d'autre.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun CountersSection(state: ProfileUiState, actions: ProfileActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_counters_title))
        Body(stringResource(R.string.profile_counters_body))

        val goal = state.plan?.goal
        if (goal == null) {
            Body(stringResource(R.string.profile_counters_unavailable))
        } else {
            Macro.entries.forEach { macro ->
                CounterRow(
                    macro = macro,
                    shown = state.shown(macro),
                    proposed = goal[macro],
                    locked = state.form.locked(macro),
                    actions = actions,
                )
            }
        }
    }
}

/**
 * Une ligne : le chiffre, son état, et le geste qui bascule l'un dans l'autre.
 *
 * **La pastille porte l'état et le geste à la fois.** Elle dit que ce compteur est fixé
 * à la main, et c'est elle qu'on touche pour le rendre au calcul — c'est-à-dire la
 * « confirmation explicite » que [docs/02][parcours] exige avant qu'un recalcul reprenne
 * la main dessus. Un marqueur d'un côté et un bouton de l'autre auraient laissé croire
 * à deux notions distinctes.
 *
 * Quand il est fixé, l'écran affiche **aussi ce que le calcul proposerait**. Sans ce
 * repère, un compteur verrouillé il y a trois semaines resterait un chiffre sans
 * référence, et on ne saurait plus s'il vaut encore la peine d'être tenu.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun CounterRow(macro: Macro, shown: Double?, proposed: Double, locked: Boolean, actions: ProfileActions) {
    val label = stringResource(macro.labelRes)
    val unit = stringResource(macro.unitRes)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (locked) {
            DraftTextField(
                // `initial` n'est relu qu'a la premiere composition (D45), et c'est
                // exactement ce qu'il faut ici : un compteur fixe ne doit pas bouger
                // quand le reste du profil est corrige. C'est tout le propos, et cet
                // ecran est donc le seul du projet ou D51 ne s'applique pas.
                initial = shown?.roundToInt()?.toString().orEmpty(),
                onValueChange = { actions.onCounterChange(macro, it.toDoubleOrNull()) },
                label = stringResource(R.string.profile_counter_field, label, unit),
                keyboardType = KeyboardType.Number,
                accept = { it.isWholeNumberField() },
                modifier = Modifier.fillMaxWidth(),
            )
            Body(stringResource(R.string.profile_counter_proposed, proposed.roundToInt(), unit))
        } else {
            Text(
                text = stringResource(R.string.profile_counter_line, label, proposed.roundToInt(), unit),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        NeonChip(
            label = stringResource(R.string.profile_counter_locked),
            selected = locked,
            onClick = { if (locked) actions.onRelease(macro) else actions.onLock(macro) },
            // Six pastilles portant le meme libelle seraient six cases identiques
            // pour TalkBack : la phrase annoncee nomme le compteur.
            contentDescription = stringResource(R.string.profile_counter_locked_a11y, label),
        )
    }
}

private val Macro.labelRes: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.profile_macro_calories
        Macro.PROTEIN -> R.string.profile_macro_protein
        Macro.CARBS -> R.string.profile_macro_carbs
        Macro.SUGARS -> R.string.profile_macro_sugars
        Macro.FAT -> R.string.profile_macro_fat
        Macro.FIBER -> R.string.profile_macro_fiber
    }

/** Les calories sont en kcal, les cinq autres en grammes entiers ([D52][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private val Macro.unitRes: Int
    get() = if (this == Macro.CALORIES) R.string.profile_unit_kcal else R.string.profile_unit_gram
