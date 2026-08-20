package app.hexaphore.core.designsystem.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Un champ dont l'affichage ne dépend d'aucun aller-retour d'état.
 *
 * **Le texte affiché vit ici**, et non dans le `ViewModel`. La forme habituelle —
 * `value = state.texte`, `onValueChange = { viewModel.change(it) }` — suppose que
 * l'état revienne avant la frappe suivante. Il ne revient pas toujours : entre la
 * frappe et le nouvel état, il y a un `StateFlow`, un `combine` et une
 * recomposition, et une frappe rapide arrive avant la fin du trajet. Le champ se
 * réaffiche alors avec un texte d'il y a deux caractères, et la position du curseur
 * repart avec lui — on tape « Bolognaise », on lit « Boognaseil ».
 *
 * Ici, chaque frappe est appliquée immédiatement à l'état local ; le `ViewModel` est
 * prévenu ensuite et ne renvoie rien. Il n'y a plus qu'un seul écrivain, donc plus
 * de course.
 *
 * **Il vit dans le design system parce que la règle vaut pour tout champ du projet**
 * ([D45][decisions]), et qu'une seconde copie dans un autre écran divergerait le
 * jour où l'une des deux apprendrait quelque chose que l'autre ignore.
 *
 * [initial] n'est lu qu'à la première composition. C'est voulu et suffisant : chaque
 * champ est identifié par sa position dans une liste à clés, donc rouvrir un
 * formulaire le reconstruit avec le bon texte, et rien d'autre ne réécrit ce que
 * l'utilisateur tape.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @param accept ce que le champ laisse entrer. Une frappe refusée ne change rien —
 *   ni ici, ni dans le brouillon —, ce qui évite qu'une saisie devienne
 *   silencieusement invalide à cause d'un caractère parasite.
 * @param visualTransformation ce qui s'affiche à la place de ce qui est saisi. Elle
 *   masque une clé d'API sans la remplacer : [docs/05][ia] veut un champ masqué qui se
 *   révèle à la demande, donc un texte qu'on peut relire mais qui ne traîne pas à
 *   l'écran. Une chaîne d'astérisques stockée à la place aurait fait enregistrer les
 *   astérisques.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
fun DraftTextField(
    initial: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /**
     * Le nombre de lignes visibles, et **ce que fait la touche entrée**.
     *
     * Au-delà d'une, le champ cesse d'être sur une seule ligne et la touche entrée y
     * saute une ligne au lieu de valider : c'est ce qu'attend une description de repas,
     * où l'on énumère. Un champ d'une ligne, lui, garde son comportement — la touche
     * emmène au champ suivant.
     */
    minLines: Int = 1,
    /**
     * `true` quand ce champ est **celui qui manque**.
     *
     * Il se colore, et son libellé avec. Un formulaire de vingt-quatre champs dont un
     * seul bloque l'enregistrement ne se parcourt pas à l'œil : c'est le champ qui
     * doit se désigner, pas l'utilisateur qui doit le chercher.
     */
    isError: Boolean = false,
    accept: (String) -> Boolean = { true },
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }

    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (accept(candidate.text)) {
                value = candidate
                onValueChange(candidate.text)
            }
        },
        label = { Text(text = label, color = if (isError) MaterialTheme.colorScheme.error else labelColor) },
        isError = isError,
        singleLine = minLines == 1,
        minLines = minLines,
        // Decimal et non Number : le separateur decimal doit etre atteignable, et
        // la virgule est ce que produit un clavier en francais.
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (minLines == 1) ImeAction.Next else ImeAction.Default,
        ),
        visualTransformation = visualTransformation,
        modifier = modifier,
    )
}

/**
 * Ce qu'un champ numérique laisse entrer.
 *
 * Des chiffres et **au plus un** séparateur décimal, virgule ou point. Le clavier
 * décimal d'Android laisse passer plus que ça selon les fabricants, et « 12,5,3 » ne
 * se convertit en aucun nombre : la saisie deviendrait invalide sans que rien ne dise
 * pourquoi.
 *
 * Le champ **refuse** la frappe plutôt que de l'accepter puis de la nettoyer.
 * Nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur —
 * exactement le défaut que [DraftTextField] évite.
 */
fun String.isNumberField(): Boolean =
    all { it.isDigit() || it in DECIMAL_SEPARATORS } && count { it in DECIMAL_SEPARATORS } <= 1

/**
 * Ce qu'un champ de valeur nutritionnelle laisse entrer : des chiffres, rien d'autre.
 *
 * **Les six valeurs sont des grammes entiers.** Personne ne compte les demi-grammes
 * de lipides, et une décimale affichée est une précision promise que la source ne
 * tient pas — CIQUAL donne 0,25 g de protéines pour une pomme parce que la mesure
 * est en dessous du seuil de quantification, pas parce qu'elle vaut un quart de
 * gramme ([D52][decisions]).
 *
 * Le séparateur décimal disparaît donc du clavier **et** du filtre : laisser taper
 * « 12,5 » pour l'arrondir ensuite obligerait à réécrire le texte affiché, donc à
 * repositionner le curseur — exactement ce que [DraftTextField] évite.
 *
 * [decisions]: docs/11-decisions.md
 */
fun String.isWholeNumberField(): Boolean = all { it.isDigit() }

private const val DECIMAL_SEPARATORS = ",."
