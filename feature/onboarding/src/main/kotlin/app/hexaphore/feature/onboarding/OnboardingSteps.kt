package app.hexaphore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexaphore.core.designsystem.component.DraftTextField
import app.hexaphore.core.designsystem.component.NeonChip
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.goal.GoalStrategy
import app.hexaphore.domain.profile.ActivityLevel
import app.hexaphore.domain.profile.Sex
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** **1. Accueil.** Le nom, une phrase, et l'avertissement à accepter pour continuer. */
@Composable
internal fun WelcomeStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_welcome_title))
        Body(stringResource(R.string.onboarding_welcome_body))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = answers.disclaimerAccepted, onCheckedChange = {
                onAnswers(answers.copy(disclaimerAccepted = it))
            })
            Body(stringResource(R.string.onboarding_disclaimer))
        }
    }
}

/**
 * **2. Vous.** Date de naissance, sexe, taille, poids actuel.
 *
 * Rien n'est pré-rempli. Une valeur plausible affichée dans un champ serait acceptée
 * par distraction, et l'objectif calculé serait celui de quelqu'un d'autre.
 */
@Composable
internal fun AboutYouStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_you_title))

        DraftTextField(
            initial = answers.birthDate?.toString().orEmpty(),
            onValueChange = { text -> text.toLocalDateOrNull()?.let { onAnswers(answers.copy(birthDate = it)) } },
            label = stringResource(R.string.onboarding_birth_date),
            keyboardType = KeyboardType.Number,
            accept = { it.length <= ISO_DATE_LENGTH },
            modifier = Modifier.fillMaxWidth(),
        )

        Body(stringResource(R.string.onboarding_sex_label))
        ChoiceRow(
            options = Sex.entries,
            selected = answers.sex,
            label = { stringResource(it.labelRes) },
            onSelect = { onAnswers(answers.copy(sex = it)) },
        )
        // La troisieme option applique la moyenne des deux formules, et l'ecran le
        // dit : cacher ce detail produirait un chiffre inexplicable.
        if (answers.sex == Sex.UNSPECIFIED) Body(stringResource(R.string.onboarding_sex_unspecified_note))

        NumberField(
            value = answers.heightCm,
            label = stringResource(R.string.onboarding_height),
            onValueChange = { onAnswers(answers.copy(heightCm = it)) },
        )
        NumberField(
            value = answers.currentWeightKg,
            label = stringResource(R.string.onboarding_weight),
            onValueChange = { onAnswers(answers.copy(currentWeightKg = it)) },
        )
    }
}

/**
 * **3. Activité.** Cinq niveaux, chacun décrit par un exemple concret.
 *
 * « Modérément actif » ne veut rien dire ; « sport 3 à 5 fois par semaine » se répond
 * en une seconde.
 */
@Composable
internal fun ActivityStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_activity_title))
        Body(stringResource(R.string.onboarding_activity_body))

        ActivityLevel.entries.forEach { level ->
            NeonChip(
                label = stringResource(level.labelRes),
                selected = level == answers.activityLevel,
                onClick = { onAnswers(answers.copy(activityLevel = level)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** **4. Objectif.** Trois cartes, puis poids cible et échéance. */
@Composable
internal fun ObjectiveStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_objective_title))

        ChoiceRow(
            options = GoalStrategy.entries,
            selected = answers.strategy,
            label = { stringResource(it.labelRes) },
            onSelect = { onAnswers(answers.copy(strategy = it)) },
        )

        if (answers.strategy != null && answers.strategy != GoalStrategy.MAINTAIN) {
            NumberField(
                value = answers.targetWeightKg,
                label = stringResource(R.string.onboarding_target_weight),
                onValueChange = { onAnswers(answers.copy(targetWeightKg = it)) },
            )
            DraftTextField(
                initial = answers.targetDate?.toString().orEmpty(),
                onValueChange = { text -> onAnswers(answers.copy(targetDate = text.toLocalDateOrNull())) },
                label = stringResource(R.string.onboarding_target_date),
                keyboardType = KeyboardType.Number,
                accept = { it.length <= ISO_DATE_LENGTH },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
        accept = { text -> text.isEmpty() || text.matches(DECIMAL) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
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
internal fun StepTitle(text: String) {
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

private const val ISO_DATE_LENGTH = 10
private val DECIMAL = Regex("""\d{0,3}([.,]\d{0,2})?""")

/** Une date incomplète n'est pas une erreur : c'est une saisie en cours. */
private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }
    .getOrElse { if (it is DateTimeParseException) null else throw it }

internal fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private val Sex.labelRes: Int
    get() = when (this) {
        Sex.MALE -> R.string.onboarding_sex_male
        Sex.FEMALE -> R.string.onboarding_sex_female
        Sex.UNSPECIFIED -> R.string.onboarding_sex_unspecified
    }

private val ActivityLevel.labelRes: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.onboarding_activity_sedentary
        ActivityLevel.LIGHT -> R.string.onboarding_activity_light
        ActivityLevel.MODERATE -> R.string.onboarding_activity_moderate
        ActivityLevel.ACTIVE -> R.string.onboarding_activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.onboarding_activity_very_active
    }

private val GoalStrategy.labelRes: Int
    get() = when (this) {
        GoalStrategy.LOSE -> R.string.onboarding_strategy_lose
        GoalStrategy.MAINTAIN -> R.string.onboarding_strategy_maintain
        GoalStrategy.GAIN -> R.string.onboarding_strategy_gain
    }
