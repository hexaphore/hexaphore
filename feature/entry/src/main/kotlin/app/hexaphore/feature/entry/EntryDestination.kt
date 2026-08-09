package app.hexaphore.feature.entry

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexaphore.domain.diary.DishId
import app.hexaphore.domain.food.FoodId
import kotlinx.serialization.Serializable

/**
 * L'écran de validation, comme destination.
 *
 * `dishId` nul veut dire « nouvelle saisie » ; `foodId` renseigné veut dire qu'elle
 * démarre sur une fiche choisie ailleurs.
 *
 * **Ce sont deux identités, pas deux modes.** L'écran ne reçoit toujours rien qui
 * lui permette de brancher sur la provenance : il ne lit ni l'un ni l'autre, c'est
 * le `ViewModel` qui en fait un brouillon, et le brouillon reste la seule chose que
 * l'écran connaisse. C'est ce qui évite d'avoir à le généraliser à chaque mode.
 *
 * Des identifiants plutôt que le brouillon lui-même : une route est sérialisée dans
 * l'état de navigation, et y faire transiter un plat entier reviendrait à en tenir
 * une seconde copie que rien ne tiendrait à jour.
 *
 * **Ce chemin s'arrête à une ligne.** La photo de la tranche 6 en produira cinq, et
 * une route ne les portera pas : il faudra un brouillon en attente, partagé entre
 * l'écran qui le produit et celui qui le valide. Ajouter ici un argument par mode
 * serait la première marche vers l'écran à quatre branches que le projet refuse.
 */
@Serializable
data class EntryDestination(val dishId: String? = null, val foodId: String? = null) {
    internal companion object {
        /**
         * Le nom sous lequel l'argument arrive dans le `SavedStateHandle`.
         *
         * Lu dans le descripteur du sérialiseur plutôt que recopié : c'est le nom
         * de la propriété ci-dessus, et le recopier créerait deux endroits à
         * renommer là où le compilateur n'en signalerait qu'un.
         *
         * Pourquoi ne pas passer par `toRoute<EntryDestination>()`, qui donnerait
         * l'objet entier : son décodeur construit un `android.os.Bundle`, donc il
         * exige un appareil ou Robolectric. Un `ViewModel` qu'on ne peut pas
         * instancier dans un test JVM pour lire un argument est un mauvais échange.
         */
        val DISH_ID: String = EntryDestination::dishId.name

        /** Idem, pour la fiche d'où part une saisie neuve. */
        val FOOD_ID: String = EntryDestination::foodId.name
    }
}

/** Ouvre une saisie neuve, ou la modification d'un plat existant. */
fun NavController.navigateToEntry(dishId: DishId? = null) {
    navigate(EntryDestination(dishId = dishId?.value))
}

/** Ouvre une saisie neuve, préremplie depuis une fiche d'aliment. */
fun NavController.navigateToEntryFor(foodId: FoodId) {
    navigate(EntryDestination(foodId = foodId.value))
}

/**
 * Déclare l'écran dans un graphe.
 *
 * Le module expose son entrée plutôt que sa composable : `:app` assemble des
 * graphes sans avoir à connaître les arguments de chaque écran.
 */
fun NavGraphBuilder.entryScreen(onClose: () -> Unit) {
    composable<EntryDestination> {
        EntryRoute(onClose = onClose)
    }
}
