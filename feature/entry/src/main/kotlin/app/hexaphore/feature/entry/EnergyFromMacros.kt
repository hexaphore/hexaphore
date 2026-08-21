package app.hexaphore.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.nutrition.EnergyProposal
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.energyProposal
import kotlin.math.roundToInt

/**
 * Le calcul de l'énergie que l'écran **propose**, et le geste qui l'accepte.
 *
 * Corriger les macros d'une ligne laissait l'énergie inchangée — et c'est elle qui
 * décide si la ligne est enregistrable. Le calcul du règlement UE 1169/2011 la déduit
 * des quatre autres, mais il ne s'impose jamais : c'est une pastille à toucher, pas
 * un champ qui se remplit tout seul. Un écran qui écrirait dans un champ que
 * quelqu'un vient de remplir ferait exactement ce que `edited` existe pour empêcher.
 *
 * La règle elle-même vit dans le domaine ([app.hexaphore.domain.nutrition.energyProposal]) :
 * ce fichier ne fait que la montrer et la rendre saisissable.
 *
 * @see docs/12-plan-de-developpement.md
 */
@Composable
internal fun EnergyProposalRow(line: EntryFormLine, actions: EntryActions) {
    val proposal = line.energyProposal ?: return
    val kcal = proposal.kcal.roundToInt()

    // Le libelle dit d'ou vient le chiffre ; la description dit ce que l'appui va
    // faire. Les deux sont necessaires : « 297 kcal » annonce a TalkBack ne laisse
    // pas deviner qu'il **remplace** ce qui est deja dans le champ.
    val spoken = stringResource(R.string.entry_energy_proposal_a11y, kcal)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AssistChip(
            onClick = { actions.onLineEdit(line.id, LineEdit.AcceptEnergy) },
            label = { Text(stringResource(R.string.entry_energy_proposal, kcal)) },
            modifier = Modifier.semantics { contentDescription = spoken },
        )
        if (proposal.withoutFiber) {
            // La valeur est minoree d'au plus deux kilocalories par gramme de fibres
            // ignore. Une valeur minoree qui s'annonce debloque la ligne ; la meme
            // valeur silencieuse serait un chiffre invente de plus.
            Text(
                text = stringResource(R.string.entry_energy_without_fiber),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ce que les macros de cette ligne donneraient, s'il y a lieu de le proposer. */
internal val EntryFormLine.energyProposal: EnergyProposal?
    get() = toDraftLine().values.energyProposal

/**
 * La même ligne, son énergie remplacée par le calcul.
 *
 * **Trois gestes en un, et aucun n'est facultatif.**
 *
 * La valeur est écrite arrondie à l'entier, comme les six champs le sont partout
 * ailleurs : ce qui est affiché est ce qui sera enregistré ([D52][decisions]).
 *
 * Elle est marquée `edited`, parce qu'elle est exactement ce que ce marqueur décrit —
 * une valeur que l'utilisateur a voulue. Elle cesse donc de suivre la quantité, ce qui
 * est le seul comportement cohérent : les macros dont elle se déduit ne la suivent
 * déjà plus.
 *
 * Et [EntryFormLine.revision] avance, sans quoi **rien ne se verrait**. Un champ de
 * saisie tient son propre texte et ne relit sa valeur initiale qu'à la première
 * composition ([D45][decisions]) : le brouillon porterait la nouvelle énergie pendant
 * que le champ afficherait l'ancienne. C'est le défaut qui s'est produit trois fois
 * dans cette tranche, toujours sous la même forme — la donnée était juste, l'écran ne
 * la montrait pas.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun EntryFormLine.withComputedEnergy(): EntryFormLine {
    val proposal = energyProposal ?: return this
    return copy(
        macros = macros + (Macro.CALORIES to proposal.kcal.roundToInt().toString()),
        edited = edited + Macro.CALORIES,
        revision = revision + 1,
    )
}
