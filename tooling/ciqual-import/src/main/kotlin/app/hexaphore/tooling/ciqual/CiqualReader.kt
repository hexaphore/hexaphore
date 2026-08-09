package app.hexaphore.tooling.ciqual

/** Une teneur dont l'écriture n'est reconnue par aucune règle du parseur. */
internal data class UnrecognisedValue(val foodCode: String, val constCode: String, val raw: String) {
    override fun toString(): String = "aliment $foodCode, constituant $constCode : [$raw]"
}

/** Ce que l'archive contient, une fois lue et interprétée. */
internal data class CiqualTable(val foods: List<CiqualFood>, val unrecognised: List<UnrecognisedValue>)

/**
 * L'assemblage : trois fichiers croisés pour produire une fiche par aliment.
 *
 * L'ordre de lecture n'est pas indifférent. Les constituants sont vérifiés
 * **avant** tout le reste : découvrir une renumérotation après avoir traversé 69 Mo
 * serait une minute perdue à chaque essai, et surtout une occasion de ne pas voir
 * l'erreur parce qu'elle arriverait au milieu d'un flot de messages.
 */
internal class CiqualReader(private val archive: CiqualArchive) {
    fun read(): CiqualTable {
        verifyConstituents()
        val groups = readGroups()
        val names = readFoods(groups)
        val (nutrients, unrecognised) = readCompositions(names.keys)

        val foods =
            names.map { (code, identity) ->
                CiqualFood(
                    code = code,
                    name = identity.name,
                    groupName = identity.groupName,
                    nutrients = nutrients[code].orEmpty(),
                )
            }
        return CiqualTable(foods.sortedBy { it.code }, unrecognised)
    }

    /**
     * Le contrôle qui protège d'une erreur qu'aucun test ne verrait.
     *
     * Si l'ANSES renumérotait ses constituants, l'import continuerait de tourner et
     * remplirait la colonne des lipides avec autre chose. La base se générerait,
     * l'application se lancerait, et les chiffres seraient faux.
     */
    private fun verifyConstituents() {
        val labels = mutableMapOf<String, String>()
        archive.constituents { record ->
            val code = record["const_code"].orEmpty()
            if (Nutrient.byCode(code) != null) labels[code] = record["const_nom_fr"].orEmpty()
        }

        val drifted =
            Nutrient.entries.filter { labels[it.constCode] != it.expectedLabel }.map {
                "  ${it.name} (code ${it.constCode}) : attendu [${it.expectedLabel}], lu [${labels[it.constCode]}]"
            }

        if (drifted.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Les codes de constituants CIQUAL ne portent plus les memes intitules :")
                    drifted.forEach { appendLine(it) }
                    appendLine()
                    append("Verifier const_*.xml et corriger Nutrient.kt avant de regenerer la base.")
                },
            )
        }
    }

    /**
     * Le rayon d'un aliment.
     *
     * Le sous-groupe plutôt que le groupe : « soupes » situe mieux que « entrées et
     * plats composés », et c'est ce qui départage deux résultats de recherche
     * homonymes. Le groupe sert de repli pour les aliments qui n'ont pas de
     * sous-groupe renseigné.
     */
    private fun readGroups(): Map<String, String> {
        val names = mutableMapOf<String, String>()
        archive.groups { record ->
            record["alim_grp_code"]?.let { names.putIfAbsent(it, record["alim_grp_nom_fr"].orEmpty()) }
            record["alim_ssgrp_code"]?.let { names.putIfAbsent(it, record["alim_ssgrp_nom_fr"].orEmpty()) }
        }
        return names.filterValues { it.isNotBlank() && it != "-" }
    }

    private fun readFoods(groups: Map<String, String>): Map<String, FoodIdentity> {
        val identities = linkedMapOf<String, FoodIdentity>()
        archive.foods { record ->
            val code = record["alim_code"].orEmpty()
            val name = record["alim_nom_fr"].orEmpty()
            if (code.isBlank() || name.isBlank()) return@foods
            identities[code] =
                FoodIdentity(
                    name = name,
                    groupName = groups[record["alim_ssgrp_code"]] ?: groups[record["alim_grp_code"]],
                )
        }
        return identities
    }

    /**
     * Les teneurs, filtrées aux huit colonnes retenues.
     *
     * `CiqualValue.Unknown` **n'est pas stocké** : l'absence de clé est l'inconnu.
     * C'est ce qui rend impossible de le confondre avec zéro plus loin — il n'y a
     * rien à confondre, et aucune valeur par défaut à écrire nulle part.
     */
    private fun readCompositions(
        known: Set<String>,
    ): Pair<Map<String, Map<Nutrient, Double>>, List<UnrecognisedValue>> {
        val values = mutableMapOf<String, MutableMap<Nutrient, Double>>()
        val unrecognised = mutableListOf<UnrecognisedValue>()

        archive.compositions { record ->
            val code = record["alim_code"].orEmpty()
            val nutrient = Nutrient.byCode(record["const_code"].orEmpty())
            if (nutrient == null || code !in known) return@compositions

            when (val value = CiqualValueParser.parse(record["teneur"])) {
                is CiqualValue.Known -> values.getOrPut(code) { mutableMapOf() }[nutrient] = value.amount
                CiqualValue.Unknown -> Unit
                is CiqualValue.Unrecognised ->
                    unrecognised += UnrecognisedValue(code, nutrient.constCode, value.raw)
            }
        }
        return values to unrecognised
    }

    private data class FoodIdentity(val name: String, val groupName: String?)
}
