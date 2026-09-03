package app.hexavore.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.hexavore.core.designsystem.R
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.profile.centimetresToFeetAndInches
import app.hexavore.domain.profile.feetAndInchesToCentimetres
import app.hexavore.domain.profile.kilogramsToPounds
import app.hexavore.domain.profile.poundsToKilograms
import kotlin.math.roundToInt

/**
 * Les deux mesures du corps, saisies dans le système choisi et **rangées en métrique**.
 *
 * **Ici et non dans chaque écran** : le profil, l'onboarding et le journal de poids
 * posent la même question, et trois copies de la conversion auraient divergé le jour où
 * l'une d'elles apprend quelque chose. C'est la leçon que ce projet a déjà payée deux
 * fois.
 *
 * **La conversion vit au bord du champ.** Le formulaire garde des kilogrammes et des
 * centimètres ; ces composables les montrent autrement et retraduisent ce qu'on tape.
 * Rien ne dérive, parce que le champ est **non contrôlé** ([D45][decisions]) : il ne rend
 * une valeur que si quelqu'un a frappé une touche, et un profil qu'on ouvre puis referme
 * ressort avec le chiffre exact qui y était.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
fun BodyWeightField(
    kilograms: Double?,
    units: UnitSystem,
    label: String,
    onKilograms: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imperial = units == UnitSystem.IMPERIAL

    NumberField(
        value = kilograms?.let { if (imperial) kilogramsToPounds(it).rounded() else it },
        label = measureLabel(label, if (imperial) R.string.ds_unit_pound else R.string.ds_unit_kg),
        onValueChange = { saisi -> onKilograms(saisi?.let { if (imperial) poundsToKilograms(it) else it }) },
        modifier = modifier,
    )
}

/**
 * La taille : un champ en métrique, **deux en impérial**.
 *
 * Personne n'énonce sa taille en pouces, donc le formulaire change de forme plutôt que
 * de demander un calcul. Les deux champs se relisent ensemble : taper les pieds sans les
 * pouces donne une taille ronde, ce qui est exactement ce qu'on voulait dire.
 */
@Composable
fun HeightFields(
    centimetres: Double?,
    units: UnitSystem,
    label: String,
    onCentimetres: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (units == UnitSystem.METRIC) {
        NumberField(
            value = centimetres,
            label = measureLabel(label, R.string.ds_unit_cm),
            onValueChange = onCentimetres,
            modifier = modifier,
        )
        return
    }

    val taille = centimetres?.let { centimetresToFeetAndInches(it) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NumberField(
            value = taille?.feet?.toDouble(),
            label = measureLabel(label, R.string.ds_unit_feet),
            onValueChange = { pieds -> onCentimetres(joined(pieds, taille?.inches?.toDouble())) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = taille?.inches?.toDouble(),
            label = measureLabel(label, R.string.ds_unit_inches),
            onValueChange = { pouces -> onCentimetres(joined(taille?.feet?.toDouble(), pouces)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** « Poids actuel » et « kg » deviennent « Poids actuel (kg) ». L'écran dit quoi, le composant dit en quoi. */
@Composable
private fun measureLabel(label: String, unitRes: Int): String =
    stringResource(R.string.ds_measure_label, label, stringResource(unitRes))

/**
 * Deux champs, une mesure.
 *
 * Vider les deux rend `null` — la taille n'est pas renseignée. Vider **un** des deux le
 * lit comme un zéro : « 5 pieds » tout court est une taille, pas une absence.
 */
private fun joined(feet: Double?, inches: Double?): Double? = when {
    feet == null && inches == null -> null
    else -> feetAndInchesToCentimetres(feet?.roundToInt() ?: 0, inches?.roundToInt() ?: 0)
}

/**
 * Un champ numérique qui refuse une frappe non numérique au lieu de la nettoyer.
 *
 * Nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur
 * ([D45][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun NumberField(value: Double?, label: String, onValueChange: (Double?) -> Unit, modifier: Modifier) {
    DraftTextField(
        initial = value?.let { formatted(it) }.orEmpty(),
        onValueChange = { text -> onValueChange(text.replace(',', '.').toDoubleOrNull()) },
        label = label,
        keyboardType = KeyboardType.Decimal,
        accept = { it.isNumberField() },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Un dixième d'unité : 0,1 lb pèse 45 g, ce qui est déjà sous le bruit d'une balance. */
private fun Double.rounded(): Double = (this * TENTHS).roundToInt() / TENTHS

private fun formatted(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.rounded().toString()

private const val TENTHS = 10.0
