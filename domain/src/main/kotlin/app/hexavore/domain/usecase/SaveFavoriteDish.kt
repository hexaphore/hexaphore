package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.diary.FavoriteNumbering
import app.hexavore.domain.identity.IdGenerator

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
 * **Sauf pour une ligne corrigée à la main**, qui entre **déliée** de sa fiche. Le
 * modèle vivant est une bonne règle tant que la fiche dit vrai ; celui qui a complété
 * lui-même les valeurs d'un aliment mal renseigné a précisément dit le contraire, et
 * rejouer la fiche lui reprendrait son travail sans prévenir.
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
        // **Une ligne corrigee a la main se delie de sa fiche.** Le favori est un
        // modele vivant : une ligne qui cite une fiche se rejoue depuis la fiche
        // courante, et corriger ses flocons corrige tous les petits-dejeuners a venir
        // (D62). Mais celui qui a complete lui-meme les valeurs d'une fiche
        // incomplete -- la feta, les capres, une ligne proposee par un modele --
        // n'attend pas qu'on les lui reprenne au rejeu. Sa correction gagne, et le
        // lien tombe : c'est la meme regle que `edited` fait deja respecter au
        // recalcul, appliquee au rejeu.
        foodId = line.foodId.takeIf { line.edited.isEmpty() },
        name = line.name.trim(),
        quantity = checkNotNull(line.quantity) { "Ligne sans quantite : ${line.name}" },
        unit = line.unit,
        grams = checkNotNull(line.grams) { "Ligne sans quantite : ${line.name}" },
        values = line.values,
    )
}

/**
 * Réécrit un favori existant, **sans enregistrer de repas**.
 *
 * C'est la seconde vie de l'écran de validation : il sert d'éditeur au modèle
 * lui-même. Le plat n'entre pas au journal — on est venu corriger un modèle, pas noter
 * un repas —, et le nom du favori ne change pas : on le modifie depuis sa liste, où
 * il est déjà nommé.
 *
 * **Les plats déjà enregistrés qui citaient ce favori le perdent.** Pas de
 * répercussion en chaîne : leurs lignes ne bougent pas d'un gramme. Ce qui tombe est
 * la **provenance** — « rejoué depuis les Flocons du matin » n'est plus vérifiable
 * quand les Flocons du matin ont changé de contenu. Un lien qui ment vaut moins qu'un
 * lien absent.
 */
class UpdateFavoriteDish(private val favorites: FavoriteDishes, private val diary: DiaryRepository) {
    /**
     * @return `null` si le favori a disparu entre l'ouverture et l'enregistrement.
     * @throws IllegalArgumentException si le brouillon n'a aucune ligne enregistrable :
     *   un favori vidé ne rejouerait rien, et ce n'est pas ainsi qu'on le supprime.
     */
    suspend operator fun invoke(draft: EntryDraft, id: FavoriteDishId): FavoriteDish? {
        require(draft.lines.isNotEmpty() && draft.lines.all { it.complete }) {
            "Brouillon incomplet : un favori sans ligne enregistrable ne rejouerait rien."
        }

        val existing = favorites.byId(id) ?: return null
        favorites.save(existing.copy(components = draft.toComponents()))
        diary.unlinkFavorite(id)

        return existing
    }
}

/**
 * Le premier numéro libre pour nommer un plat favori.
 *
 * **Un numéro et non une phrase** : « Plat » est un mot d'interface, et le domaine
 * n'en écrit pas. Ce qu'il sait, lui, c'est lesquels sont déjà pris.
 *
 * La proposition précédente était la liste des aliments du plat — « Riz blanc cuit,
 * Blanc de poulet sans peau, Haricots verts appertisés égouttés ». Les libellés de
 * l'ANSES sont à rallonge, et trois d'entre eux font un titre de cinquante caractères
 * qu'on efface au lieu de le corriger. Un numéro se garde ou se remplace, mais il ne
 * se subit pas.
 */
class NextFavoriteNumber(private val numbering: FavoriteNumbering, private val favorites: FavoriteDishes) {
    /**
     * @param taken dit si un nom est déjà pris, tel que l'écran l'écrirait.
     *
     * **Le compteur avance, et le nom pris est enjambé.** Les deux règles se
     * complètent : le compteur garantit qu'un numéro ne réapparaît pas après une
     * suppression, la vérification qu'il ne heurte pas un favori nommé « Plat 4 » à la
     * main. Une lambda plutôt qu'un patron de chaîne, parce que c'est l'appelant qui
     * connaît le mot.
     *
     * La borne évite une boucle sans fin si quelque chose répondait « pris » à tout.
     */
    suspend operator fun invoke(taken: (Int) -> String): Int {
        var number = numbering.next()
        var attempts = 0
        while (attempts < MAX_ATTEMPTS && favorites.nameTaken(taken(number))) {
            number = numbering.next()
            attempts++
        }
        return number
    }
}

/** Cent noms déjà pris d'affilée : au-delà, le nom proposé n'est plus le sujet. */
private const val MAX_ATTEMPTS = 100
