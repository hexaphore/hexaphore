package app.hexavore.domain.diary

/**
 * Le numéro du prochain plat nommé automatiquement.
 *
 * **Il ne redescend jamais.** Le premier numéro libre — « après avoir supprimé Plat 1,
 * le suivant s'appellerait Plat 1 » — rendait deux favoris successifs indiscernables
 * dans un historique de captures d'écran, et faisait réapparaître un nom qu'on venait
 * d'écarter. Un compteur qui avance rend chaque nom proposé unique dans le temps.
 *
 * **Invisible** : rien ne l'affiche, et il ne se remet à zéro qu'avec les données de
 * l'application. Il ne compte pas les favoris — il compte les propositions faites.
 */
interface FavoriteNumbering {
    /** Le numéro à proposer, et **il est consommé** : deux appels ne rendent pas le même. */
    suspend fun next(): Int
}
