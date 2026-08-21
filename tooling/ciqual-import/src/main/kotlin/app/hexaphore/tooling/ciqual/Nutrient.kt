package app.hexaphore.tooling.ciqual

import app.hexaphore.domain.food.FoodCategory
import app.hexaphore.domain.nutrition.Macro

/**
 * Les colonnes de CIQUAL que l'application retient, et le code sous lequel l'ANSES
 * les publie.
 *
 * **Le code fait foi, le libellé le vérifie.** Désigner une colonne par son intitulé
 * — `Energie, Règlement UE N° 1169/2011 (kcal/100 g)` — reviendrait à faire dépendre
 * l'import d'une chaîne accentuée, avec ses espaces et son `N°`, dans un fichier qui
 * change une fois par an. Mais ne se fier qu'au code laisserait une renumérotation
 * silencieuse remplir la colonne des lipides avec des glucides.
 *
 * Les deux sont donc déclarés, et l'import **échoue** si `const_code` ne porte plus
 * le libellé attendu. C'est la seule vérification qui protège d'une erreur qu'aucun
 * test ne verrait : la base se génère, l'application se lance, et les chiffres sont
 * faux.
 *
 * Les deux dernières colonnes ne sont pas affichées en v1. Elles sont collectées
 * quand même : la donnée est là, la prendre coûte zéro, et ne pas la prendre
 * coûterait un import complet à refaire le jour où on décide de les montrer
 * ([docs/07][modele]).
 *
 * Le nom de colonne vit ici aussi, et pas dans le schéma SQL. C'est ce qui rend
 * impossible le défaut auquel cette table se prête : réordonner l'énumération et
 * décaler silencieusement huit colonnes, de sorte que les lipides reçoivent les
 * glucides. Une seule liste, un seul ordre.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
enum class Nutrient(val constCode: String, val column: String, val expectedLabel: String) {
    KCAL("328", "kcal_100", "Energie, Règlement UE N° 1169/2011 (kcal/100 g)"),
    PROTEIN("25000", "protein_100", "Protéines, N x facteur de Jones (g/100 g)"),
    CARB("31000", "carb_100", "Glucides (g/100 g)"),
    SUGAR("32000", "sugar_100", "Sucres (g/100 g)"),
    FAT("40000", "fat_100", "Lipides (g/100 g)"),
    FIBER("34100", "fiber_100", "Fibres alimentaires (g/100 g)"),
    SATURATED_FAT("40302", "saturated_fat_100", "AG saturés (g/100 g)"),
    SALT("10004", "salt_100", "Sel chlorure de sodium (g/100 g)"),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.constCode }

        fun byCode(code: String): Nutrient? = BY_CODE[code]
    }
}

/** Un aliment CIQUAL, une fois ses teneurs lues et interprétées. */
data class CiqualFood(
    val code: String,
    val name: String,
    val groupName: String?,
    /** Le rayon du bandeau de recherche, ou `null` s'il n'entre dans aucun. */
    val category: FoodCategory?,
    val nutrients: Map<Nutrient, Double>,
) {
    /**
     * Une teneur, ou `null` si l'ANSES ne l'a pas déterminée.
     *
     * L'absence de clé **est** l'inconnu : une valeur `CiqualValue.Unknown` n'entre
     * jamais dans la table. C'est ce qui rend impossible de la confondre avec zéro
     * plus loin dans la chaîne — il n'y a rien à confondre.
     */
    operator fun get(nutrient: Nutrient): Double? = nutrients[nutrient]
}

/** Une portion usuelle, lue dans `servings.csv`. */
data class CiqualServing(val code: String, val label: String, val grams: Double, val isDefault: Boolean)

/**
 * La colonne CIQUAL que chacun des six compteurs de l'application désigne.
 *
 * Deux vocabulaires pour la même chose : [Macro] est celui du domaine, [Nutrient]
 * celui de la table de l'ANSES — qui en publie deux de plus, non affichées. Un `when`
 * exhaustif plutôt qu'une correspondance par nom : ajouter un septième compteur
 * cesserait de compiler ici, là où une table de chaînes se serait tue.
 */
internal val Macro.nutrient: Nutrient
    get() = when (this) {
        Macro.CALORIES -> Nutrient.KCAL
        Macro.PROTEIN -> Nutrient.PROTEIN
        Macro.CARBS -> Nutrient.CARB
        Macro.SUGARS -> Nutrient.SUGAR
        Macro.FAT -> Nutrient.FAT
        Macro.FIBER -> Nutrient.FIBER
    }
