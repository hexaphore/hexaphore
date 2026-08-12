package app.hexaphore.integration.openfoodfacts

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ce que l'application dit d'elle-même à Open Food Facts.
 *
 * `Hexaphore/<version> (github.com/hexaphore/hexaphore)` — l'adresse est celle de
 * l'organisation et non d'un compte personnel : elle survit à un changement de
 * propriétaire, à un passage en association et à un départ de son auteur
 * ([D26][decisions]). La version vient du `versionName`, pour qu'un signalement
 * désigne un binaire précis plutôt que « l'application ».
 *
 * **Elle est fournie par `:app` et non calculée ici**, parce que c'est l'application
 * qui a une version, pas ce module. Deux lectures du catalogue finiraient par
 * annoncer deux numéros.
 *
 * **Une `data class` et non une `value class`**, bien qu'elle n'enveloppe qu'une
 * chaîne : Dagger décore le nom d'une fonction qui prend une classe en ligne, et le
 * nom décoré n'est pas un identifiant Java valide. La génération échoue alors sur un
 * `IllegalArgumentException: not a valid name` qui ne dit rien de sa cause.
 *
 * [decisions]: docs/11-decisions.md
 */
data class ClientIdentity(val userAgent: String)

/**
 * L'en-tête obligatoire, posé une fois pour toutes les requêtes.
 *
 * **Open Food Facts bloque les clients anonymes**, et c'est légitime : l'en-tête est
 * leur seul moyen de joindre l'auteur d'un client qui se comporte mal. Le piège est
 * qu'un refus ne ressemble pas à un refus — la requête part, quelque chose revient,
 * et le symptôme a l'allure d'une panne réseau ([D26][decisions]).
 *
 * **Un intercepteur et non un `@Header` par méthode** : une deuxième requête ajoutée
 * un jour l'aurait sinon oublié, et l'oubli ne se serait vu qu'en production.
 *
 * [decisions]: docs/11-decisions.md
 */
internal class UserAgentInterceptor(private val identity: ClientIdentity) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request()
            .newBuilder()
            .header("User-Agent", identity.userAgent)
            .build(),
    )
}
