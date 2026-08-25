package app.hexavore.domain.ai

import app.hexavore.domain.notice.KeyRejection

/**
 * Le reconnaisseur, qui retient au passage si le fournisseur a refusé la clé.
 *
 * **Un décorateur et non une ligne dans les deux écrans d'IA.** La photo et la
 * description appellent le même port ; leur demander à chacun de noter l'issue aurait
 * fait deux endroits à tenir d'accord, et le troisième chemin — celui qui n'existe pas
 * encore — serait arrivé sans. Ici, la règle est vraie par construction : tout ce qui
 * passe par le port passe par elle.
 *
 * **La sonde n'y passe pas, et c'est voulu.** Le bouton « Tester » affiche son résultat
 * à l'écran, sous le doigt qui vient d'appuyer : l'échec y est déjà dit, et une
 * pastille ne rompt un silence que là où il y en a un.
 *
 * Un succès efface le souvenir. Une autre panne — réseau, quota, délai — ne le crée ni
 * ne l'efface : elle ne dit rien de la clé, et l'écraser sur un timeout ferait
 * disparaître une pastille juste pour la mauvaise raison.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
class NotingRecognizer(private val delegate: FoodRecognizer, private val rejection: KeyRejection) : FoodRecognizer {
    override suspend fun recognize(input: RecognitionInput): RecognitionOutcome {
        val outcome = delegate.recognize(input)
        when {
            outcome is RecognitionOutcome.Recognized -> rejection.clear()
            outcome is RecognitionOutcome.Failed && outcome.error == AiError.InvalidKey -> rejection.note()
            else -> Unit
        }
        return outcome
    }
}
