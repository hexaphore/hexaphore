package app.hexavore.domain.food

/**
 * Retrouver une fiche par son identifiant.
 *
 * Un port à une méthode, pour un seul appelant : l'écran de validation, qui reçoit
 * l'identifiant d'un aliment par sa route et doit en faire une ligne préremplie. Une
 * route porte des identifiants et non des objets — elle est sérialisée dans l'état
 * de navigation, et y faire transiter une fiche entière reviendrait à en tenir une
 * seconde copie que rien ne tiendrait à jour.
 *
 * Séparé des autres ports du catalogue pour la raison habituelle : l'écran de
 * validation n'a rien à faire de la recherche, des favoris ni de la création.
 */
fun interface FoodLookup {
    /** @return `null` si la fiche a été supprimée entre-temps. */
    suspend fun byId(id: FoodId): Food?
}
