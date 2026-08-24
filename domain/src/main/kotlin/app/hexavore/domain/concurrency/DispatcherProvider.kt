package app.hexavore.domain.concurrency

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Les dispatchers de coroutines, fournis par injection.
 *
 * Un `Dispatchers.IO` écrit en dur dans un cas d'usage rend ce cas d'usage
 * dépendant d'un vrai pool de threads : le test doit alors synchroniser, attendre,
 * et devient intermittent. Passer par ce port permet à un test de tout rabattre sur
 * un dispatcher de test, sans règle globale ni substitution statique.
 *
 * Ce port est déclaré dès maintenant bien qu'aucun code ne l'utilise encore. C'est
 * une exception assumée au principe « pas d'abstraction préventive » : il figure
 * parmi les contraintes que le plan de développement déclare non rattrapables, au
 * même titre que [app.hexavore.domain.time.Clock]. La conséquence de l'ajouter
 * trop tard n'est pas une refonte, c'est une centaine d'appels à corriger un par un.
 *
 * @see docs/06-architecture.md
 * @see docs/12-plan-de-developpement.md
 */
interface DispatcherProvider {
    /** Fil principal. Réservé à ce qui touche l'interface. */
    val main: CoroutineDispatcher

    /** Calcul. Résumés de journée, agrégations, conversions. */
    val default: CoroutineDispatcher

    /** Entrées-sorties bloquantes : base de données, fichiers, réseau. */
    val io: CoroutineDispatcher
}
