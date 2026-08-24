package app.hexavore.domain.usecase

import app.hexavore.domain.food.CustomFoodDraft
import app.hexavore.domain.food.Food
import app.hexavore.domain.food.FoodId
import app.hexavore.domain.food.FoodSource
import app.hexavore.domain.food.FoodStore
import app.hexavore.domain.identity.IdGenerator

/**
 * Enregistre un aliment personnel, nouveau ou corrigé.
 *
 * **Modifier une fiche ne touche à aucune entrée de journal.** Les six macros y sont
 * figées à l'enregistrement, et ce cas d'usage n'a aucun moyen de les atteindre :
 * il n'écrit que dans le catalogue ([D05][decisions]). C'est cette tranche qui rend
 * la règle éprouvable pour la première fois, puisqu'un aliment existe enfin pour
 * être modifié.
 *
 * L'identifiant vient d'[IdGenerator] et non de `UUID.randomUUID()` : sans le port,
 * un test ne pourrait affirmer que « une fiche a été écrite », pas « celle-là ».
 *
 * [decisions]: docs/11-decisions.md
 */
class SaveCustomFood(private val store: FoodStore, private val ids: IdGenerator) {
    /**
     * @throws IllegalArgumentException si la fiche n'a ni nom ni énergie. Le
     *   formulaire empêche le cas ; la vérification est là pour qu'un futur appelant
     *   ne puisse pas écrire une fiche inexploitable sans s'en apercevoir.
     */
    suspend operator fun invoke(draft: CustomFoodDraft): FoodId {
        require(draft.complete) { "Fiche incomplete : un aliment personnel demande un nom et une energie." }

        return store.save(
            Food(
                id = draft.id ?: FoodId(ids.next()),
                source = FoodSource.CUSTOM,
                // La reference d'un aliment personnel est son code-barres, quand il
                // en a un. C'est par elle que le prochain scan le retrouvera, et
                // c'est ce qui fait tenir la promesse « un produit absent d'Open Food
                // Facts se cree a la main **en conservant son code-barres** ».
                sourceRef = draft.barcode?.value,
                name = draft.name.trim(),
                // Une marque vide est une absence de marque, pas une marque vide.
                brand = draft.brand.trim().ifBlank { null },
                per100g = draft.per100g,
                defaultServingG = draft.defaultServingG,
            ),
        )
    }
}
