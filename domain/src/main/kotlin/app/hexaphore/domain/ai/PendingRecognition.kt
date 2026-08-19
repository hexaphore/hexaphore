package app.hexaphore.domain.ai

import app.hexaphore.domain.diary.EntrySource
import java.util.concurrent.atomic.AtomicReference

/**
 * Ce qu'un modèle vient de proposer, en attendant l'écran qui le validera.
 *
 * **Une route ne peut pas porter cinq lignes**, et `EntryDestination` l'avait écrit
 * avant que le cas existe : les arguments de navigation sont sérialisés dans l'état du
 * système, et y faire transiter un plat entier reviendrait à en tenir une seconde
 * copie que rien ne tiendrait à jour. Le canal de résultat qui sert à « Ajouter un
 * aliment » ne convient pas davantage — il rend une valeur à l'écran **précédent**,
 * alors qu'ici l'écran destinataire n'existe pas encore.
 *
 * D'où ce dépôt, et **il ne contient jamais qu'une proposition** : celle qui vient
 * d'être faite. Il n'y a pas de file d'attente parce qu'il n'y a pas deux analyses en
 * vol — l'écran de capture attend la sienne avant de laisser en lancer une autre.
 *
 * **Ce qui est déposé est la réponse du modèle, pas un brouillon.** La résolution
 * demande le catalogue, donc elle appartient à `OpenDraft`, avec les quatre autres
 * origines. Déposer un brouillon déjà résolu ferait de l'écran de capture un second
 * endroit qui sait fabriquer un plat.
 *
 * **Volatil, et c'est voulu.** Une proposition ne survit pas à la mort du processus :
 * ce qui a été payé au fournisseur est perdu, et l'écran de validation dira qu'il n'y
 * a rien à valider. La persister demanderait de décider où, combien de temps, et sous
 * quelle forme une proposition périmée se relit — trois questions qu'aucun usage ne
 * pose aujourd'hui.
 */
interface PendingRecognition {
    /** Dépose la proposition. Une seconde remplace la première. */
    fun offer(recognition: Recognition, source: EntrySource)

    /**
     * Reprend la proposition et **vide le dépôt**.
     *
     * Consommée plutôt que lue : revenir sur l'écran de validation par le bouton
     * « retour » du système ne doit pas ressusciter un plat qu'on vient d'enregistrer,
     * ni le dédoubler. `null` quand il n'y a rien — la deuxième ouverture, ou un
     * processus qui a redémarré entre les deux écrans.
     */
    fun take(): ProposedMeal?
}

/** Une proposition et le mode de capture d'où elle vient. */
data class ProposedMeal(val recognition: Recognition, val source: EntrySource)

/**
 * Le dépôt, en mémoire.
 *
 * Dans le domaine et non dans un module de données : il ne range rien, il fait passer.
 * Le rendre persistant serait un changement de nature, pas d'implémentation.
 *
 * `AtomicReference` parce que l'analyse finit sur un dispatcher d'entrées-sorties et
 * que la reprise a lieu sur le thread principal.
 */
class InMemoryPendingRecognition : PendingRecognition {
    private val pending = AtomicReference<ProposedMeal?>(null)

    override fun offer(recognition: Recognition, source: EntrySource) {
        pending.set(ProposedMeal(recognition, source))
    }

    override fun take(): ProposedMeal? = pending.getAndSet(null)
}
