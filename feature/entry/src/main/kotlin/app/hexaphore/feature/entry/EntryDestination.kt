package app.hexaphore.feature.entry

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.hexaphore.domain.diary.DishId
import kotlinx.serialization.Serializable

/**
 * L'écran de validation, comme destination.
 *
 * `dishId` nul veut dire « nouvelle saisie ». C'est le seul argument, et il le
 * restera : cet écran ne doit jamais recevoir de quoi deviner **d'où** vient ce
 * qu'il montre, sans quoi il finirait par s'en servir — et il faudrait alors le
 * généraliser à chaque nouveau mode de saisie. Le contenu arrive par le brouillon,
 * pas par la route.
 *
 * Un identifiant plutôt que le brouillon lui-même : une route est sérialisée dans
 * l'état de navigation, et y faire transiter un plat entier reviendrait à en tenir
 * une seconde copie que rien ne tiendrait à jour.
 */
@Serializable
data class EntryDestination(val dishId: String? = null) {
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
    }
}

/** Ouvre une saisie neuve, ou la modification d'un plat existant. */
fun NavController.navigateToEntry(dishId: DishId? = null) {
    navigate(EntryDestination(dishId?.value))
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
