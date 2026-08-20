package app.hexaphore.feature.entry

import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.usecase.FavoriteOutcome
import app.hexaphore.domain.usecase.NextFavoriteNumber
import app.hexaphore.domain.usecase.RemoveFavoriteDish
import app.hexaphore.domain.usecase.SaveFavoriteDish
import javax.inject.Inject

/**
 * Les trois gestes que l'étoile déclenche, en un objet.
 *
 * **Un regroupement et non une couche de plus** : ce sont trois cas d'usage du
 * domaine, passés ensemble parce qu'ils répondent à une même question — que fait
 * l'étoile de ce plat. Passés un par un, ils poussaient le constructeur du `ViewModel`
 * au-delà du seuil de paramètres, et la réponse du projet est de regrouper selon ce
 * que les choses sont plutôt que de relever le seuil.
 */
class DraftFavorites @Inject constructor(
    private val saveFavoriteDish: SaveFavoriteDish,
    private val removeFavoriteDish: RemoveFavoriteDish,
    private val nextFavoriteNumber: NextFavoriteNumber,
) {
    suspend fun save(draft: EntryDraft, name: String, existing: FavoriteDishId?): FavoriteOutcome =
        saveFavoriteDish(draft, name, existing)

    suspend fun remove(id: FavoriteDishId) = removeFavoriteDish(id)

    /** Le premier numéro libre : « Plat 3 ». Le mot vient de l'écran, le compte d'ici. */
    suspend fun nextNumber(label: (Int) -> String): Int = nextFavoriteNumber(label)
}
