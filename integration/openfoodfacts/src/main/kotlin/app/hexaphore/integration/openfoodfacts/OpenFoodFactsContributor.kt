package app.hexaphore.integration.openfoodfacts

import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.domain.food.ContributionOutcome
import app.hexaphore.domain.food.FoodContribution
import app.hexaphore.domain.food.FoodContributionTarget
import app.hexaphore.domain.food.OffAccount
import app.hexaphore.domain.nutrition.Macro
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * La contribution d'une fiche à Open Food Facts.
 *
 * **Aucun retrait exponentiel, contrairement à la lecture.** Ce n'est pas un oubli :
 * une lecture qu'on rejoue trois fois ne coûte que du temps, une écriture rejouée
 * sans que personne l'ait demandé est une action sortante de plus. Le service range
 * par code-barres, donc un second envoi ne créerait pas de doublon — mais c'est
 * précisément parce que réessayer est **sûr** que le geste peut rester à
 * l'utilisateur, qui voit l'échec et rappuie s'il le veut.
 *
 * @param sandbox `true` pour viser l'instance de test. Un réglage, pas une variante
 *   de compilation : c'est en le basculant qu'on vérifie qu'un envoi aboutit, et une
 *   `buildConfigField` demanderait de réinstaller l'application pour ça.
 *
 * @param liveUrl,sandboxUrl **les deux instances, injectées et non lues d'une
 *   constante.** Elles y étaient d'abord, et le premier test l'a montré de la pire
 *   façon : le contributeur visait `world.openfoodfacts.org` quel que soit le serveur
 *   local, donc le cas partait pour de vrai sur la base publique et attendait ensuite
 *   une requête qui n'arriverait jamais. Une URL en dur dans une classe qui écrit
 *   dehors n'est pas un détail de câblage — c'est ce qui décide si un test est un test.
 *
 * @see docs/04-sources-de-donnees.md
 */
internal class OpenFoodFactsContributor(
    private val api: ContributionApi,
    private val dispatchers: DispatcherProvider,
    private val sandbox: suspend () -> Boolean,
    private val liveUrl: String = OPEN_FOOD_FACTS_BASE_URL,
    private val sandboxUrl: String = OPEN_FOOD_FACTS_SANDBOX_URL,
) : FoodContributionTarget {
    override suspend fun contribute(contribution: FoodContribution, account: OffAccount): ContributionOutcome =
        withContext(dispatchers.io) {
            val base = if (sandbox()) sandboxUrl else liveUrl
            try {
                api.contribute(base + CONTRIBUTION_PATH, contribution.asFields(account)).toOutcome()
            } catch (offline: IOException) {
                offline.toUnreachableContribution()
            }
        }

    /**
     * Ce que le service a répondu, traduit en conduite.
     *
     * **Le code HTTP d'abord, le corps ensuite.** Un `401` ou un `403` dit que le
     * compte est refusé, et réessayer n'y changera rien ; tout le reste se lit dans
     * `status`. Les confondre ferait proposer de réessayer à quelqu'un dont le mot de
     * passe est faux.
     */
    private fun Response<ContributionEnvelope>.toOutcome(): ContributionOutcome {
        val envelope = body()
        return when {
            code() in REJECTED_CODES -> ContributionOutcome.Rejected
            !isSuccessful || envelope == null -> ContributionOutcome.Unreachable
            envelope.status == SAVED_STATUS -> ContributionOutcome.Sent
            else -> ContributionOutcome.Refused(envelope.statusVerbose.ifBlank { UNSTATED_REASON })
        }
    }
}

/**
 * La fiche, sous la forme de champs plats que le script attend.
 *
 * **Les noms viennent de la documentation d'Open Food Facts, pas de ma mémoire** :
 * `code`, `product_name`, `brands`, `serving_size`, `nutrition_data_per`, et
 * `nutriment_<id>` avec son `nutriment_<id>_unit`. Les six identifiants de teneur
 * sont ceux que le module lit déjà en sens inverse — `energy-kcal`, `proteins`,
 * `carbohydrates`, `sugars`, `fat`, `fiber` —, donc éprouvés contre un vrai serveur
 * depuis [D63][decisions]. Les inventer aurait produit une fiche acceptée et vide.
 *
 * **Une valeur inconnue ne part pas.** Elle n'est pas envoyée à zéro : l'envoyer
 * écrirait dans une base publique une mesure que personne n'a faite, et c'est la
 * règle du projet appliquée là où elle sort de l'appareil.
 *
 * [decisions]: docs/11-decisions.md
 */
private fun FoodContribution.asFields(account: OffAccount): Map<String, String> = buildMap {
    put("user_id", account.userId)
    put("password", account.password)
    put("code", barcode.value)
    put("product_name", name)
    brand?.let { put("brands", it) }
    // Les teneurs sont donnees pour cent grammes, ce que le service ne devine pas.
    put("nutrition_data_per", PER_100G)
    servingGrams?.let { put("serving_size", "${it.asPlainNumber()} g") }

    Macro.entries.forEach { macro ->
        per100g[macro]?.let { value ->
            put("nutriment_${macro.offId}", value.asPlainNumber())
            put("nutriment_${macro.offId}_unit", macro.offUnit)
        }
    }
}

/**
 * Le nom de la teneur chez Open Food Facts.
 *
 * Un `when` exhaustif : ajouter un septième compteur cesserait de compiler ici plutôt
 * que de partir sous un nom que le service ignore — et un champ inconnu est accepté
 * en silence par ce script, donc perdu sans que rien ne le dise.
 */
private val Macro.offId: String
    get() = when (this) {
        Macro.CALORIES -> "energy-kcal"
        Macro.PROTEIN -> "proteins"
        Macro.CARBS -> "carbohydrates"
        Macro.SUGARS -> "sugars"
        Macro.FAT -> "fat"
        Macro.FIBER -> "fiber"
    }

/** L'énergie en kilocalories, les cinq autres en grammes. */
private val Macro.offUnit: String get() = if (this == Macro.CALORIES) "kcal" else "g"

/**
 * Un nombre tel qu'un formulaire l'attend : un point décimal, et pas de notation
 * scientifique.
 *
 * `toString()` sur un `Double` rend « 1.0E-4 » sur une petite valeur, que le script
 * lirait comme 1. Et la virgule d'une locale française ferait la même chose en pire :
 * « 2,4 » deviendrait 2.
 */
private fun Double.asPlainNumber(): String = when {
    this == toLong().toDouble() -> toLong().toString()
    else -> java.math.BigDecimal(this).setScale(DECIMALS, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString()
}

/** Ce qu'on montre quand le service refuse sans dire pourquoi. */
private const val UNSTATED_REASON = "refus sans motif"

/** Hors ligne, DNS muet, connexion coupée : l'écran dit la même chose des trois. */
private fun IOException.toUnreachableContribution(): ContributionOutcome = ContributionOutcome.Unreachable

private const val SAVED_STATUS = 1
private const val PER_100G = "100g"
private const val DECIMALS = 3
private val REJECTED_CODES = setOf(401, 403)
