package app.hexaphore.domain.usecase

import app.hexaphore.domain.diary.EntryDraft
import app.hexaphore.domain.diary.FavoriteComponent
import app.hexaphore.domain.diary.FavoriteDish
import app.hexaphore.domain.diary.FavoriteDishId
import app.hexaphore.domain.diary.FavoriteDishes
import app.hexaphore.domain.identity.IdGenerator

/**
 * Ce qu'a donné une mise en favori.
 *
 * Un type de retour plutôt qu'une exception : un nom déjà pris n'est pas une panne,
 * c'est une réponse. L'écran la traduit en phrase et laisse le champ ouvert, là où une
 * exception aurait obligé à distinguer, dans un `runCatching`, ce qui se corrige de ce
 * qui se réessaie.
 */
sealed interface FavoriteOutcome {
    data class Saved(val id: FavoriteDishId) : FavoriteOutcome

    /** Un autre favori porte déjà ce nom, aux accents et à la casse près. */
    data object NameTaken : FavoriteOutcome
}

/**
 * Enregistre un brouillon comme plat favori.
 *
 * **Le favori est un modèle, pas une copie du journal.** Chaque ligne y entre avec la
 * fiche dont elle vient quand elle en vient d'une, pour que « mes flocons du matin »
 * reflète la fiche courante au prochain rejeu ([docs/07][modele]) ; ses valeurs sont
 * enregistrées en plus, comme contenu d'une ligne tapée à la main et comme repli le
 * jour où la fiche citée aura disparu ([D62][decisions]).
 *
 * **Le nom est unique.** Deux « Petit-déj » dans une liste ne se distinguent plus, et
 * choisir devient un pari. La vérification est faite ici, et la base la double d'un
 * index : une règle d'unicité tenue par la seule discipline d'écriture n'en est pas une.
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
class SaveFavoriteDish(private val favorites: FavoriteDishes, private val ids: IdGenerator) {
    /**
     * @param existing le favori qu'on renomme, ou `null` pour en créer un.
     * @throws IllegalArgumentException si le brouillon n'a aucune ligne complète : un
     *   favori sans contenu ne rejouerait rien.
     */
    suspend operator fun invoke(draft: EntryDraft, name: String, existing: FavoriteDishId? = null): FavoriteOutcome {
        require(draft.lines.isNotEmpty() && draft.lines.all { it.complete }) {
            "Brouillon incomplet : un favori sans ligne enregistrable ne rejouerait rien."
        }

        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Un favori sans nom serait introuvable." }

        return if (favorites.nameTaken(trimmed, excluding = existing)) {
            FavoriteOutcome.NameTaken
        } else {
            val id = existing ?: FavoriteDishId(ids.next())
            favorites.save(FavoriteDish(id = id, name = trimmed, components = draft.toComponents()))
            FavoriteOutcome.Saved(id)
        }
    }
}

/**
 * Les lignes du brouillon, sous la forme qu'un favori conserve.
 *
 * Ni identifiant de ligne, ni identifiant d'entrée de journal : un favori n'est pas
 * une saisie, et rejouer le même favori deux fois doit produire deux plats distincts.
 * Les traîner ici aurait fait écrire le second par-dessus le premier.
 */
private fun EntryDraft.toComponents(): List<FavoriteComponent> = lines.map { line ->
    FavoriteComponent(
        foodId = line.foodId,
        name = line.name.trim(),
        quantity = checkNotNull(line.quantity) { "Ligne sans quantite : ${line.name}" },
        unit = line.unit,
        grams = checkNotNull(line.grams) { "Ligne sans quantite : ${line.name}" },
        values = line.values,
    )
}
