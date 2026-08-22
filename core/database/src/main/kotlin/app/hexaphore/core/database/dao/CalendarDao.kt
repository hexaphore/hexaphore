package app.hexaphore.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * La relecture d'un historique, par plages de jours.
 *
 * **Séparé de [DiaryDao], et pas pour tenir un seuil.** Celui-là sert la journée
 * courante — lire aujourd'hui, écrire, supprimer, annuler — et change quand l'écran de
 * saisie change. Celui-ci sert le calendrier, et change quand la façon de relire son
 * historique change. Ce sont deux raisons de changer, donc deux objets — le même
 * découpage que `FoodMarksDao` et `FoodCitationsDao` face à `FoodDao` ([D71][decisions]).
 *
 * **Aucun `SUM` ici non plus.** Agréger en SQL traiterait `NULL` comme absent, ce qui
 * est correct arithmétiquement mais perd l'information qu'une valeur manquait. Les
 * plats remontent entiers et le domaine totalise, jour par jour, en retenant quels
 * totaux sont minorés ([D29][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Dao
interface CalendarDao {
    /**
     * Les plats d'une plage, bornes incluses.
     *
     * La comparaison porte sur des dates ISO stockées en texte : leur ordre
     * lexicographique **est** leur ordre chronologique, ce qui est vrai de
     * `AAAA-MM-JJ` et de rien d'autre. C'est aussi pourquoi ce format n'est pas
     * négociable dans ce schéma.
     */
    @Transaction
    @Query("SELECT * FROM dish WHERE date BETWEEN :from AND :to ORDER BY date ASC, logged_at ASC")
    fun observeRange(from: String, to: String): Flow<List<DishWithEntries>>
}
