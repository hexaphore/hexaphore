package app.hexavore.feature.entry

import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.profile.UnitSystem
import app.hexavore.domain.usecase.AddFoodLine
import app.hexavore.domain.usecase.DraftOrigin
import app.hexavore.domain.usecase.ObserveUnitSystem
import app.hexavore.domain.usecase.OpenDraft
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * De quoi composer un brouillon : l'ouvrir, lui ajouter une ligne, savoir en quelles
 * unités elle se saisit.
 *
 * **Né quand le seuil de paramètres a mordu**, et le découpage suit ce que les choses
 * sont plutôt qu'un compte : à côté vivent la journée qu'on regarde, l'enregistrement et
 * les favoris — trois autres sujets. Ici, tout ce qui répond à « de quoi ce plat est-il
 * fait ». C'est la même forme que [DraftFavorites], née de la même façon.
 *
 * Le système d'unités y a sa place et non ailleurs : il ne décide de rien d'autre que
 * de la paire proposée sur une ligne.
 */
class DraftComposition @Inject constructor(
    private val openDraft: OpenDraft,
    private val addFoodLine: AddFoodLine,
    private val observeUnitSystem: ObserveUnitSystem,
) {
    suspend fun open(origin: DraftOrigin): EntryDraft? = openDraft(origin)

    suspend fun line(id: FoodId): DraftLine? = addFoodLine(id)

    fun units(): Flow<UnitSystem> = observeUnitSystem()
}
