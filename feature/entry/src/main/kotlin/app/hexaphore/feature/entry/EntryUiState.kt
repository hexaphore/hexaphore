package app.hexaphore.feature.entry

import app.hexaphore.domain.diary.DraftImpact

/**
 * L'état de l'écran de validation.
 *
 * @see docs/06-architecture.md
 */
internal sealed interface EntryUiState {
    /** Avant que le plat à modifier n'ait été relu. Immédiat pour une saisie neuve. */
    data object Loading : EntryUiState

    data class Content(
        val form: EntryForm,
        /**
         * `null` quand la journée n'a pas pu être lue.
         *
         * L'écran renonce alors au « il vous restera », et à rien d'autre : refuser
         * la saisie parce qu'on n'a pas pu lire le reste de la journée ferait perdre
         * le repas pour une information de confort.
         */
        val impact: DraftImpact?,
        /** `true` pendant l'écriture : le bouton d'enregistrement devient inerte. */
        val saving: Boolean = false,
        /**
         * `true` quand le dernier nom de favori proposé était déjà pris.
         *
         * Porté par l'état plutôt que rendu par l'appel : la boîte de nommage reste
         * ouverte avec le nom refusé dedans, et c'est ce qui permet de le corriger
         * plutôt que de tout retaper.
         */
        val favoriteNameTaken: Boolean = false,
    ) : EntryUiState {
        /** `true` quand ce plat est dans la liste des favoris. */
        val favorite: Boolean get() = form.favoriteId != null

        /**
         * Un plat sans ligne enregistrable ne peut pas devenir un favori.
         *
         * Il ne rejouerait rien. L'étoile est donc absente plutôt que refusante : il
         * n'y a rien à expliquer sur un brouillon qu'on est en train de remplir.
         */
        val favoritable: Boolean get() = form.toDraft().let {
            it.lines.isNotEmpty() && it.lines.all { l -> l.complete }
        }
        val saveable: Boolean get() = !saving && form.toDraft().saveable

        /**
         * `true` quand enregistrer **supprimerait** le plat, faute de ligne restante.
         *
         * L'écran s'en sert pour changer le libellé du bouton. Sans cela, retirer la
         * dernière ligne puis appuyer sur « Enregistrer » ferait disparaître le plat
         * sans que rien ne l'ait annoncé — et le geste est le même que celui qui, une
         * ligne plus tôt, enregistrait une correction.
         */
        val emptying: Boolean get() = form.toDraft().emptying
    }

    /**
     * Le plat n'a pas pu être rouvert.
     *
     * Supprimé pendant qu'on le modifiait, ou illisible. Le cas n'est pas théorique :
     * la suppression par balayage de l'accueil est immédiate, et rien n'empêche
     * d'avoir ouvert la modification avant. Les deux causes appellent le même geste
     * — quitter l'écran — donc elles ne sont pas distinguées ; annoncer « supprimé »
     * quand la base est simplement illisible serait pire qu'imprécis, ce serait faux.
     */
    data object Unavailable : EntryUiState

    /** L'écriture a échoué. Le brouillon est conservé, il n'y a qu'à réessayer. */
    data class Error(val form: EntryForm) : EntryUiState

    /** Écrit. L'écran se referme. */
    data object Saved : EntryUiState
}
