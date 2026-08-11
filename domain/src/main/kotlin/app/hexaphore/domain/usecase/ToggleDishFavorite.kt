package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.Dish

/**
 * Met un plat du journal en favori, ou l'en retire.
 *
 * C'est ce que déclenche l'appui long depuis l'accueil. Le geste est une **bascule**,
 * et l'état du plat dit laquelle des deux moitiés s'applique : un plat déjà rattaché à
 * un favori le quitte, un autre y entre sous le nom qu'on lui donne.
 *
 * **Deux écritures, dans cet ordre** : le favori d'abord, le lien ensuite. L'inverse
 * laisserait un plat désigner un favori qui n'existe pas encore — et si l'écriture du
 * favori échouait, un plat marqué comme favori sans l'être.
 *
 * Retirer ne touche pas le plat : la base délie elle-même, par `ON DELETE SET NULL`.
 * Le faire aussi ici serait tenir la règle à deux endroits, et il suffirait qu'un seul
 * change ([D62][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class ToggleDishFavorite(
    private val drafts: GetDishDraft,
    private val update: UpdateDish,
    private val save: SaveFavoriteDish,
    private val remove: RemoveFavoriteDish,
) {
    /**
     * @param name le nom du favori à créer, ignoré quand le plat en a déjà un.
     * @return `null` quand il n'y a rien à signaler — retrait, ou plat introuvable.
     */
    suspend operator fun invoke(dish: Dish, name: String?): FavoriteOutcome? {
        val existing = dish.favoriteId
        return when {
            existing != null -> remove(existing).let { null }
            name == null -> null
            else -> attach(dish, name)
        }
    }

    /** `null` quand le plat a disparu entre l'appui long et l'écriture. */
    private suspend fun attach(dish: Dish, name: String): FavoriteOutcome? {
        val draft = drafts(dish.id) ?: return null
        return save(draft, name).also { outcome ->
            if (outcome is FavoriteOutcome.Saved) update(draft.copy(favoriteId = outcome.id))
        }
    }
}
