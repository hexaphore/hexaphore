package app.hexaphore.feature.home

import app.hexaphore.domain.diary.DaySummary

/**
 * L'état de l'accueil, en un seul objet.
 *
 * Un état unique plutôt que trois champs indépendants (`isLoading`, `data`,
 * `error`) : les combinaisons impossibles — chargement *et* contenu — cessent de
 * compiler au lieu d'être évitées à la main.
 *
 * Pas de cas d'erreur pour l'instant : la lecture du journal ne peut pas échouer
 * tant qu'elle vient de la mémoire. Il apparaîtra avec Room, quand il aura quelque
 * chose à décrire — une variante d'erreur qu'aucun code ne peut produire est une
 * branche morte que les écrans doivent quand même traiter.
 *
 * @see docs/06-architecture.md
 */
sealed interface HomeUiState {
    /** Premier rendu, avant la première émission du journal. */
    data object Loading : HomeUiState

    data class Content(val summary: DaySummary) : HomeUiState
}
