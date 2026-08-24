package app.hexavore.integration.ai

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Où va la description d'une requête, quand quelqu'un veut la voir.
 *
 * **Ne rien journaliser est le défaut**, et c'est ce que rend [Silent]. Un journal
 * réseau détaillé n'existe que dans la variante `debug` ([docs/05][ia] § Sécurité des
 * clés) ; en release, il n'y a pas de journal à assainir parce qu'il n'y a pas de
 * journal.
 *
 * [ia]: docs/05-ia.md
 */
fun interface NetworkLog {
    fun record(line: String)

    companion object {
        /** Le défaut : rien ne sort. */
        val Silent: NetworkLog = NetworkLog { }
    }
}

/**
 * Ce qui garantit qu'une clé d'API ne se retrouve jamais dans un journal.
 *
 * **Il masque la description, pas la requête.** C'est toute la subtilité, et l'erreur
 * qu'on ferait en lisant son nom trop vite : retirer l'en-tête ferait échouer
 * l'authentification, et le défaut ressemblerait à une clé invalide — donc à un
 * problème de l'utilisateur. La requête part intacte ; c'est ce qu'on en **dit** qui
 * est expurgé.
 *
 * **Un intercepteur et non un appel de journalisation par fournisseur** : le
 * septième fournisseur l'aurait oublié, et l'oubli ne se serait vu qu'une fois la
 * clé de quelqu'un dans un rapport de plantage.
 *
 * **La chaîne de requête est masquée entièrement.** Certains fournisseurs acceptent
 * la clé en paramètre d'URL ; aucun de ceux qu'on appelle ne l'exige, et c'est
 * précisément pourquoi personne ne penserait à vérifier ce point le jour où l'un
 * d'eux s'y met.
 *
 * @see docs/05-ia.md § Sécurité des clés
 */
internal class RedactionInterceptor(private val log: NetworkLog) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        log.record(request.redacted())
        return chain.proceed(request)
    }
}

private fun Request.redacted(): String = "$method ${url.withoutQuery()} ${headers.redacted()}"

/**
 * L'URL sans ce qui suit le `?`.
 *
 * Le chemin est conservé — il dit quel point d'entrée a été appelé, ce qui est
 * l'essentiel de l'intérêt d'un journal réseau.
 */
private fun HttpUrl.withoutQuery(): String =
    if (querySize == 0) toString() else newBuilder().query(null).build().toString() + "?…"

private fun Headers.redacted(): String = names().sorted().joinToString(prefix = "[", postfix = "]") { name ->
    "$name: ${if (name.lowercase() in SECRET_HEADERS) "***" else this[name]}"
}

/**
 * Les trois en-têtes par lesquels une clé voyage, en minuscules.
 *
 * `Authorization` pour OpenAI, DeepSeek, Mistral et les compatibles, `x-api-key` pour
 * Anthropic, `x-goog-api-key` pour Gemini. Les trois sont déclarés dès maintenant,
 * bien qu'un seul serve : les deux autres arrivent avec leurs fournisseurs, et une
 * liste complétée plus tard est une liste qu'on complète après avoir oublié.
 */
private val SECRET_HEADERS = setOf("authorization", "x-api-key", "x-goog-api-key")
