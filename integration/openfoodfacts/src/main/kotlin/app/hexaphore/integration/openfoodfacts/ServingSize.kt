package app.hexaphore.integration.openfoodfacts

/**
 * Ce que pèse la portion écrite sur l'emballage, en grammes, ou `null`.
 *
 * `serving_size` est un champ **libre**, rempli à la main par des contributeurs :
 * on y trouve « 30 g », « 30g », « 250 ml », « 1 verre (200 ml) », « 2 biscuits »
 * et « une portion ». Ce n'est donc pas un nombre qu'on parse, c'est une phrase dans
 * laquelle on cherche une mesure — d'où l'expression régulière plutôt qu'une
 * découpe, et d'où la **première** mesure trouvée : dans « 1 verre (200 ml) », c'est
 * la seule des deux qui soit une masse.
 *
 * **Un millilitre compte pour un gramme, et ce n'est pas une approximation ici.**
 * Open Food Facts publie les teneurs d'une boisson pour 100 **ml** sous les mêmes
 * clés `_100g` ; traiter les deux unités de la même façon rend donc le calcul d'une
 * portion de 250 ml exact. La densité de [docs/04][sources] sert à autre chose : à
 * convertir un volume **tapé par l'utilisateur** pour un aliment dont la référence
 * est une masse, et elle arrive avec le résolveur de la tranche 6.
 *
 * Rien de reconnaissable rend `null` — pas 100 g. L'appelant sait quoi faire d'une
 * portion absente ; recevoir un chiffre inventé, non.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
internal fun gramsOfServing(text: String?): Double? = text
    ?.let(MEASURE::find)
    ?.let { match ->
        val amount = match.groupValues[AMOUNT_GROUP].replace(',', '.').toDoubleOrNull()
        val factor = GRAMS_PER_UNIT[match.groupValues[UNIT_GROUP].lowercase()]
        if (amount != null && factor != null) amount * factor else null
    }?.takeIf { it > 0.0 }

private const val AMOUNT_GROUP = 1
private const val UNIT_GROUP = 2

/**
 * L'ordre des unités compte : `kg` avant `g`, `ml` et `cl` avant `l`.
 *
 * Une alternative d'expression régulière retient la **première** branche qui
 * s'applique, donc « 1.5 kg » lu avec `g` en tête donnerait un gramme et demi.
 */
private val MEASURE = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|ml|cl|dl|l)\b""", RegexOption.IGNORE_CASE)

private const val GRAMS_PER_KILO = 1000.0
private const val GRAMS_PER_CENTILITRE = 10.0
private const val GRAMS_PER_DECILITRE = 100.0

private val GRAMS_PER_UNIT: Map<String, Double> = mapOf(
    "g" to 1.0,
    "kg" to GRAMS_PER_KILO,
    "ml" to 1.0,
    "cl" to GRAMS_PER_CENTILITRE,
    "dl" to GRAMS_PER_DECILITRE,
    "l" to GRAMS_PER_KILO,
)
