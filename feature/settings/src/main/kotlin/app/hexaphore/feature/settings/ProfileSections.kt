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
import app.hexaphore.core.designsystem.component.NeonDateField
import app.hexaphore.core.designsystem.component.isNumberField
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.goal.GoalHorizon
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import app.hexaphore.domain.usecase.GoalPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/**
 * **Vous.** Les quatre réponses dont le calcul de la dépense a besoin.
 *
 * Le poids est ici et non dans une section « pesée » : c'est la dernière mesure connue,
 * c'est elle qui entre dans le calcul, et la corriger est le geste le plus fréquent de
 * cet écran. Le corriger enregistre une pesée du jour — le laisser tel quel n'en invente
 * aucune, et l'écran le dit.
 */
@Composable
internal fun YouSection(form: ProfileForm, today: LocalDate, onForm: (ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_you_title))

        NeonDateField(
            value = form.birthDate,
            onValueChange = { onForm(form.copy(birthDate = it)) },
            label = stringResource(R.string.profile_birth_date),
            yearRange = (today.year - OLDEST_YEARS)..(today.year - YOUNGEST_YEARS),
            latest = today,
            modifier = Modifier.fillMaxWidth(),
        )

        Body(stringResource(R.string.profile_sex_label))
        ChoiceColumn(
            options = Sex.entries,
            selected = form.sex,
            label = { stringResource(it.labelRes) },
            onSelect = { onForm(form.copy(sex = it)) },
        )
        if (form.sex == Sex.UNSPECIFIED) Body(stringResource(R.string.profile_sex_unspecified_note))

        NumberField(
            value = form.heightCm,
            label = stringResource(R.string.profile_height),
            onValueChange = { onForm(form.copy(heightCm = it)) },
        )
        NumberField(
            value = form.currentWeightKg,
            label = stringResource(R.string.profile_weight),
            onValueChange = { onForm(form.copy(currentWeightKg = it)) },
        )
        Body(stringResource(R.string.profile_weight_note))
    }
}

/** **Votre activité.** Cinq niveaux, chacun décrit par un exemple concret. */
@Composable
internal fun ActivitySection(form: ProfileForm, onForm: (ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_activity_title))
        Body(stringResource(R.string.profile_activity_body))
        ChoiceColumn(
            options = ActivityLevel.entries,
            selected = form.activityLevel,
            label = { stringResource(it.labelRes) },
            onSelect = { onForm(form.copy(activityLevel = it)) },
        )
    }
}

/**
 * **Votre objectif.** La stratégie, le poids visé, et l'échéance.
 *
 * L'échéance en cours s'affiche **telle qu'elle est**, en toutes lettres, et les trois
 * pastilles servent à la déplacer. Elle a été choisie un autre jour : la traduire en
 * « dans 6 mois » la ferait glisser de la durée écoulée depuis, ce qui est faux, et
 * l'arrondir à la pastille la plus proche serait pire — l'écran annoncerait un rythme
 * qui n'est pas celui qui court.
 */
@Composable
internal fun ObjectiveSection(form: ProfileForm, today: LocalDate, plan: GoalPlan?, onForm: (ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionTitle(stringResource(R.string.profile_objective_title))

        ChoiceColumn(
            options = GoalStrategy.entries,
            selected = form.strategy,
            label = { stringResource(it.labelRes) },
            onSelect = { onForm(form.copy(strategy = it)) },
        )

        if (form.strategy != null && form.strategy != GoalStrategy.MAINTAIN) {
            NumberField(
                value = form.targetWeightKg,
                label = stringResource(R.string.profile_target_weight),
                onValueChange = { onForm(form.copy(targetWeightKg = it)) },
            )

            form.targetDate?.let { Body(stringResource(R.string.profile_horizon_current, it.formatLong())) }
            Body(stringResource(R.string.profile_horizon_label))
            Horizons(form, today, plan, onForm)

            if (plan != null) {
                if (form.targetDate != null) Body(stringResource(R.string.profile_pace, abs(plan.weeklyWeightChangeKg)))
                plan.reachableOn?.let { Body(stringResource(R.string.profile_capped, it.formatLong())) }
                if (plan.carbsBelowMinimum) Body(stringResource(R.string.profile_carbs_low))
            }
        }
    }
}

@Composable
private fun Horizons(form: ProfileForm, today: LocalDate, plan: GoalPlan?, onForm: (ProfileForm) -> Unit) {
    val chosen = GoalHorizon.of(today, form.targetDate)
    GoalHorizon.entries.forEach { horizon ->
        NeonChip(
            label = stringResource(horizon.labelRes),
            selected = horizon == chosen,
            onClick = { onForm(form.copy(targetDate = horizon.dateFrom(today))) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // La date que les garde-fous ont calculee, quand ils ont mordu. Elle ne
    // correspond a aucune des trois, donc elle merite sa propre pastille.
    plan?.reachableOn?.let { date ->
        NeonChip(
            label = stringResource(R.string.profile_horizon_reachable, date.formatLong()),
            selected = form.targetDate == date,
            onClick = { onForm(form.copy(targetDate = date)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberField(value: Double?, label: String, onValueChange: (Double?) -> Unit) {
    DraftTextField(
        initial = value?.let { formatDecimal(it) }.orEmpty(),
        onValueChange = { text -> onValueChange(text.replace(',', '.').toDoubleOrNull()) },
        label = label,
        keyboardType = KeyboardType.Decimal,
        // Le champ refuse une frappe non numerique au lieu de l'accepter puis de la
        // nettoyer : nettoyer obligerait a reecrire le texte affiche, donc a
        // repositionner le curseur (D45).
        accept = { it.isNumberField() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> ChoiceColumn(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { option ->
            NeonChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Bornes de la grille des années, en années révolues avant aujourd'hui. */
private const val OLDEST_YEARS = 110
private const val YOUNGEST_YEARS = 13

internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** « 14 mars 2027 » et non « 2027-03-14 » : l'ISO n'existe que pour le stockage. */
internal fun LocalDate.formatLong(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.profile_sex_male
        Sex.FEMALE -> R.string.profile_sex_female
        Sex.UNSPECIFIED -> R.string.profile_sex_unspecified
    }

private val ActivityLevel.labelRes: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.profile_activity_sedentary
        ActivityLevel.LIGHT -> R.string.profile_activity_light
        ActivityLevel.MODERATE -> R.string.profile_activity_moderate
        ActivityLevel.ACTIVE -> R.string.profile_activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.profile_activity_very_active
    }

private val GoalStrategy.labelRes: Int
    get() = when (this) {
        GoalStrategy.LOSE -> R.string.profile_strategy_lose
        GoalStrategy.MAINTAIN -> R.string.profile_strategy_maintain
        GoalStrategy.GAIN -> R.string.profile_strategy_gain
    }

private val GoalHorizon.labelRes: Int
    get() = when (this) {
        GoalHorizon.THREE_MONTHS -> R.string.profile_horizon_three
        GoalHorizon.SIX_MONTHS -> R.string.profile_horizon_six
        GoalHorizon.TWELVE_MONTHS -> R.string.profile_horizon_twelve
    }
