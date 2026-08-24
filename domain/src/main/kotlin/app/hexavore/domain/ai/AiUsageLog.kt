package app.hexavore.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Ce que les analyses ont consommé, par fournisseur et par modèle.
 *
 * **L'utilisateur paie ses appels : il a le droit de savoir combien** ([docs/05][ia]
 * § Coût). C'est le seul endroit du projet où l'application tient un compte de ce
 * qu'elle a fait faire à quelqu'un d'autre.
 *
 * **Par modèle et pas seulement par fournisseur**, alors que [docs/05][ia] ne demande
 * qu'un compteur par fournisseur : les tarifs sont attachés aux modèles, et un compte
 * agrégé ne pourrait plus se convertir en argent. L'écran, lui, regroupe par
 * fournisseur — c'est une question d'affichage, pas de mesure.
 *
 * **Rien ne sort de l'appareil.** Ce compteur n'est pas une télémétrie : personne
 * d'autre que son propriétaire ne le lit, et [docs/01][perimetre] interdit d'ailleurs
 * toute remontée.
 *
 * [ia]: docs/05-ia.md
 * [perimetre]: docs/01-perimetre.md
 */
interface AiUsageLog {
    /**
     * Note un appel facturé.
     *
     * [usage] est `null` quand le fournisseur ne dit pas ce qu'il a compté : l'appel
     * est alors compté sans ses jetons, parce qu'il a bien eu lieu et qu'il se paiera.
     * Annoncer zéro jeton serait pire que de n'en annoncer aucun.
     */
    suspend fun record(provider: AiProvider, model: String, usage: TokenUsage?)

    fun observe(): Flow<List<AiUsageEntry>>
}

/**
 * Un compte, pour un modèle d'un fournisseur.
 *
 * [calls] compte les appels **facturés**, pas les tentatives : une clé refusée ou un
 * réseau absent n'ont rien coûté et n'y figurent pas. Une réponse illisible, si —
 * elle a été produite, donc payée.
 */
data class AiUsageEntry(val provider: AiProvider, val model: String, val calls: Int, val input: Int, val output: Int)
