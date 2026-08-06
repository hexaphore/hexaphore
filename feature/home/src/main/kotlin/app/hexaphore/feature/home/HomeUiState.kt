package app.hexaphore.feature.home

import app.hexaphore.domain.diary.DaySummary

/**
 * L'état de l'accueil, en un seul objet.
 *
 * Un état unique plutôt que trois champs indépendants (`isLoading`, `data`,
 * `error`) : les combinaisons impossibles — chargement *et* contenu — cessent de
 * compiler au lieu d'être évitées à la main.
 *
 * @see docs/06-architecture.md
 */
sealed interface HomeUiState {
    /** Premier rendu, avant la première émission du journal. */
    data object Loading : HomeUiState

    data class Content(val summary: DaySummary) : HomeUiState

    /**
     * La lecture du journal a échoué.
     *
     * Il n'y a rien de plus à dire, et c'est délibéré : une base illisible, un
     * disque plein et un fichier corrompu appellent le même geste — réessayer — et
     * un message plus précis ne servirait qu'à inquiéter avec des mots que
     * personne ne peut utiliser.
     *
     * Ce qui compte est que ce cas **existe**. Tant qu'il n'existait pas, un échec
     * de lecture se serait affiché comme une journée vide : c'est-à-dire comme un
     * mensonge, dans une application dont tout le propos est de dire la vérité sur
     * ce qu'on a mangé, y compris quand la donnée manque.
     */
    data object Error : HomeUiState
}
