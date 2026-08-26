package app.hexavore.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Si l'utilisateur veut que le modèle interroge le catalogue.
 *
 * **Un réglage global et non par fournisseur.** C'est une façon d'analyser, pas une
 * propriété d'une clé : quelqu'un qui bascule d'Anthropic vers Gemini ne s'attend pas à
 * retrouver l'analyse ordinaire sans l'avoir demandé.
 *
 * **Décoché par défaut**, parce que c'est plus lent et plus cher. La case le dit, et
 * l'utilisateur choisit en connaissance de cause.
 *
 * @see docs/04-sources-de-donnees.md
 */
interface DeepAnalysisSettings {
    fun observe(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
