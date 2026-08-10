package app.hexaphore.tooling.ciqual

import app.hexaphore.domain.food.FoodCategory

/**
 * La correspondance entre la nomenclature de l'ANSES et les huit rayons du bandeau.
 *
 * **Le code fait foi, le libellé le vérifie** — exactement comme [Nutrient], et pour
 * la même raison. Désigner un sous-groupe par son intitulé ferait dépendre l'import
 * d'une chaîne accentuée qui bouge d'une publication à l'autre ; ne se fier qu'au
 * code laisserait une renumérotation ranger les poissons dans les desserts sans que
 * rien ne le signale. Les deux sont donc déclarés, et l'import **échoue** si l'un
 * dément l'autre.
 *
 * **Sous-groupe d'abord, groupe en repli.** Le groupe 02 mélange fruits, légumes,
 * légumineuses et oléagineux : s'arrêter au groupe donnerait un seul rayon pour
 * quatre. Certains aliments n'ont pas de sous-groupe renseigné (`0000`), et c'est là
 * que le repli sert — les glaces et sorbets du groupe 08, par exemple.
 *
 * **Ce qui n'est mappé nulle part reste sans rayon**, et c'est un choix : les
 * matières grasses, les aides culinaires, les plats composés et les aliments
 * infantiles n'entrent dans aucune des huit cases. Leur en forcer une ferait
 * apparaître de l'huile de tournesol sous « Snacks ».
 *
 * @see docs/04-sources-de-donnees.md
 */
internal object CiqualCategories {
    /**
     * Version de cette table.
     *
     * Elle entre dans le nom du fichier produit : corriger un rayon ici change donc
     * l'édition livrée, et un appareil déjà installé recopie la base au lieu de
     * garder l'ancienne ([D54][decisions]).
     *
     * [decisions]: docs/11-decisions.md
     */
    const val VERSION = 1

    /**
     * Un rang de la table : un code de l'ANSES, son intitulé attendu, et le rayon.
     *
     * `null` en rayon est **déclaratif** : il dit « ce sous-groupe a été regardé et
     * n'entre dans aucune case », ce qu'un simple silence ne dirait pas. C'est ce qui
     * permet de vérifier que les 45 sous-groupes ont tous été arbitrés.
     */
    data class Mapping(val code: String, val expectedLabel: String, val category: FoodCategory?)

    /**
     * Les huit rayons, et les sous-groupes qu'ils recouvrent.
     *
     * Trois arbitrages qui ne vont pas de soi, écrits ici pour ne pas être redécouverts :
     *
     * - **Les légumineuses sont des légumes.** Nutritionnellement, une lentille est
     *   un féculent ; mais le bandeau est une aide au parcours et non une leçon, et
     *   qui touche « Légumes » cherche des lentilles.
     * - **Les fruits à coque sont des snacks.** Sous « Fruits », on cherche une
     *   pomme. Une poignée d'amandes est ce qu'on mange entre deux repas.
     * - **Les œufs sont sous « Viandes et poissons ».** L'étiquette ne les nomme pas —
     *   le groupe de l'ANSES, si — mais un œuf doit être trouvable, et il n'a nulle
     *   part ailleurs où aller.
     */
    val MAPPINGS = listOf(
        // --- Les onze groupes, qui ne servent que de repli -----------------------
        //
        // Un aliment dont le sous-groupe n'est pas renseigne retombe sur son groupe.
        // Deux d'entre eux n'ont pas de reponse honnete et restent sans rayon : le
        // groupe 02 melange fruits, legumes, legumineuses et oleagineux, et le
        // groupe 01 ne contient que des plats composes.
        Mapping("01", "entrées et plats composés", null),
        Mapping("02", "fruits, légumes, légumineuses et oléagineux", null),
        Mapping("03", "produits céréaliers", FoodCategory.FECULENTS),
        Mapping("04", "viandes, oeufs, poissons", FoodCategory.VIANDES_POISSONS),
        Mapping("05", "produits laitiers", FoodCategory.PRODUITS_LAITIERS),
        Mapping("06", "eaux et autres boissons", FoodCategory.BOISSONS),
        Mapping("07", "produits sucrés", FoodCategory.DESSERTS),
        Mapping("08", "glaces et sorbets", FoodCategory.DESSERTS),
        Mapping("09", "matières grasses", null),
        Mapping("10", "aides culinaires et ingrédients divers", null),
        Mapping("11", "aliments infantiles", null),
        // --- Les 45 sous-groupes, qui font foi -----------------------------------
        // 01 -- entrees et plats composes : un plat compose n'est pas un rayon.
        Mapping("0101", "salades composées et crudités", null),
        Mapping("0102", "soupes", null),
        Mapping("0103", "plats composés", null),
        Mapping("0104", "pizzas, tartes et crêpes salées", null),
        Mapping("0105", "sandwichs", null),
        Mapping("0106", "feuilletées et autres entrées", null),
        // 02 -- fruits, legumes, legumineuses et oleagineux : quatre rayons pour un groupe.
        Mapping("0201", "légumes", FoodCategory.LEGUMES),
        Mapping("0202", "pommes de terre et autres tubercules", FoodCategory.FECULENTS),
        Mapping("0203", "légumineuses", FoodCategory.LEGUMES),
        Mapping("0204", "fruits", FoodCategory.FRUITS),
        Mapping("0205", "fruits à coque et graines oléagineuses", FoodCategory.SNACKS),
        // 03 -- produits cerealiers.
        Mapping("0301", "pâtes, riz et céréales", FoodCategory.FECULENTS),
        Mapping("0302", "pains et assimilés", FoodCategory.FECULENTS),
        Mapping("0303", "biscuits apéritifs", FoodCategory.SNACKS),
        Mapping("0304", "farines", FoodCategory.FECULENTS),
        Mapping("0305", "pâtes à tarte", FoodCategory.FECULENTS),
        // 04 -- viandes, oeufs, poissons.
        Mapping("0401", "viandes cuites", FoodCategory.VIANDES_POISSONS),
        Mapping("0402", "viandes crues", FoodCategory.VIANDES_POISSONS),
        Mapping("0403", "charcuteries et alternatives végétales", FoodCategory.VIANDES_POISSONS),
        Mapping("0404", "autres produits à base de viande", FoodCategory.VIANDES_POISSONS),
        Mapping("0405", "poissons cuits", FoodCategory.VIANDES_POISSONS),
        Mapping("0406", "poissons crus", FoodCategory.VIANDES_POISSONS),
        Mapping("0407", "mollusques et crustacés cuits", FoodCategory.VIANDES_POISSONS),
        Mapping("0408", "mollusques et crustacés crus", FoodCategory.VIANDES_POISSONS),
        Mapping("0409", "produits à base de poissons et produits de la mer", FoodCategory.VIANDES_POISSONS),
        Mapping("0410", "oeufs", FoodCategory.VIANDES_POISSONS),
        Mapping("0411", "alternatives végétales aux produits carnés", FoodCategory.VIANDES_POISSONS),
        // 05 -- produits laitiers.
        Mapping("0501", "laits", FoodCategory.PRODUITS_LAITIERS),
        Mapping("0502", "produits laitiers frais et alternatives végétales", FoodCategory.PRODUITS_LAITIERS),
        Mapping("0503", "fromages et alternatives végétales", FoodCategory.PRODUITS_LAITIERS),
        Mapping("0504", "crèmes et spécialités à base de crème", FoodCategory.PRODUITS_LAITIERS),
        // 06 -- eaux et autres boissons. L'eau est une boisson : la chercher est legitime.
        Mapping("0601", "eaux", FoodCategory.BOISSONS),
        Mapping("0602", "boissons sans alcool", FoodCategory.BOISSONS),
        Mapping("0603", "boisson alcoolisées", FoodCategory.BOISSONS),
        // 07 -- produits sucres, partages entre ce qui se mange en dessert et ce qui
        // se grignote. Un gateau est un dessert, une barre chocolatee un snack.
        Mapping("0701", "sucres, miels et assimilés", FoodCategory.DESSERTS),
        Mapping("0702", "chocolats et produits à base de chocolat", FoodCategory.SNACKS),
        Mapping("0703", "confiseries non chocolatées", FoodCategory.SNACKS),
        Mapping("0704", "confitures et assimilés", FoodCategory.DESSERTS),
        Mapping("0705", "viennoiseries", FoodCategory.DESSERTS),
        Mapping("0706", "biscuits sucrés", FoodCategory.SNACKS),
        Mapping("0707", "céréales de petit-déjeuner", FoodCategory.FECULENTS),
        Mapping("0708", "barres céréalières", FoodCategory.SNACKS),
        Mapping("0709", "gâteaux et pâtisseries", FoodCategory.DESSERTS),
        // 08 -- glaces et sorbets. Certaines lignes n'ont pas de sous-groupe : le
        // repli sur le code de groupe, declare plus haut, les rattrape.
        Mapping("0801", "glaces", FoodCategory.DESSERTS),
        Mapping("0802", "sorbets", FoodCategory.DESSERTS),
        Mapping("0803", "desserts glacés", FoodCategory.DESSERTS),
        // 09 -- matieres grasses : aucune des huit cases. Une huile n'est pas un snack.
        Mapping("0901", "beurres", null),
        Mapping("0902", "huiles et graisses végétales", null),
        Mapping("0903", "margarines", null),
        Mapping("0904", "huiles de poissons", null),
        Mapping("0905", "autres matières grasses", null),
        // 10 -- aides culinaires et ingredients divers : un condiment ne se parcourt pas.
        Mapping("1001", "sauces", null),
        Mapping("1002", "condiments", null),
        Mapping("1003", "aides culinaires", null),
        Mapping("1004", "sels", null),
        Mapping("1005", "épices", null),
        Mapping("1006", "herbes", null),
        Mapping("1007", "algues", null),
        Mapping("1008", "denrées destinées à une alimentation particulière", null),
        Mapping("1009", "ingrédients pour végétariens", null),
        Mapping("1010", "tartinables végétariens", null),
        // 11 -- aliments infantiles : un petit pot n'est ni un legume ni un dessert.
        Mapping("1101", "laits et boissons infantiles", null),
        Mapping("1102", "petits pots salés et plats infantiles", null),
        Mapping("1103", "desserts infantiles", null),
        Mapping("1104", "céréales et biscuits infantiles", null),
    )

    private val BY_CODE = MAPPINGS.associateBy { it.code }

    /** Le rayon d'un aliment : son sous-groupe d'abord, son groupe en repli. */
    fun of(subGroupCode: String?, groupCode: String?): FoodCategory? =
        BY_CODE[subGroupCode]?.category ?: BY_CODE[groupCode]?.category

    /** Les codes que cette table connaît, pour vérifier qu'aucun n'a été oublié. */
    fun knows(code: String): Boolean = code in BY_CODE

    /**
     * Les intitulés qui ne correspondent plus, s'il y en a.
     *
     * Rendu plutôt que levé : c'est [CiqualReader] qui décide quoi en faire, comme
     * pour les constituants, et il rassemble tous les écarts en un seul message
     * plutôt qu'un par exécution.
     */
    fun drifted(labels: Map<String, String>): List<String> =
        MAPPINGS.filter { it.code in labels && labels[it.code] != it.expectedLabel }.map {
            "  ${it.code} : attendu [${it.expectedLabel}], lu [${labels[it.code]}]"
        }
}
