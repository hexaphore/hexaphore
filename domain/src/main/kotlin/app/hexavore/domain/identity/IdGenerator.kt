package app.hexavore.domain.identity

/**
 * D'où viennent les identifiants.
 *
 * Port, pour la même raison que `Clock` : un `UUID.randomUUID()` écrit au milieu
 * d'un cas d'usage est une entrée non déclarée, et une entrée non déclarée rend le
 * résultat invérifiable. Un test de `LogDish` ne peut alors affirmer que « un plat
 * a été enregistré », jamais « ce plat-là, avec ces lignes-là et ces liens-là ».
 *
 * Deux implémentations dès le premier jour, ce qui écarte le soupçon d'abstraction
 * préventive : `UuidGenerator` dans `:core:common`, et une génération déterministe
 * dans `:core:testing`.
 *
 * Les identifiants sont des UUIDv4 générés côté application, et non des entiers
 * auto-incrémentés : un compteur rendrait toute fusion de sauvegardes impossible et
 * interdirait des identifiants stables entre appareils.
 *
 * @see docs/07-modele-de-donnees.md
 */
fun interface IdGenerator {
    /** Un identifiant qui n'a jamais été rendu et ne le sera jamais deux fois. */
    fun next(): String
}
