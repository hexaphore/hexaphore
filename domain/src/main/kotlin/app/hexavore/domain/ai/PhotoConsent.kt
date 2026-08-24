package app.hexavore.domain.ai

/**
 * Le fait que l'utilisateur ait accepté, **une fois**, que ses photos partent chez un
 * tiers.
 *
 * [docs/05][ia] § Confidentialité l'exige, et exige aussi qu'on le dise *avant* la
 * première utilisation plutôt qu'en petits caractères ailleurs : le mode photo envoie
 * une image de son repas à un service qui n'est pas le nôtre, avec sa clé, sous les
 * conditions de ce service. C'est le seul endroit de l'application où une donnée
 * personnelle quitte l'appareil sans qu'un code-barres l'ait demandée.
 *
 * **Une fois, et pas à chaque photo.** Un avertissement répété n'est plus lu ; celui-ci
 * doit l'être. Il se represente si l'utilisateur efface les données de l'application —
 * c'est-à-dire quand il redevient quelqu'un qui n'a rien accepté.
 *
 * **Le mode texte n'en demande pas.** Une phrase qu'on tape pour qu'elle soit analysée
 * dit d'elle-même où elle va ; une photo prise dans l'instant, non.
 *
 * [ia]: docs/05-ia.md
 */
interface PhotoConsent {
    suspend fun accepted(): Boolean

    suspend fun accept()
}
