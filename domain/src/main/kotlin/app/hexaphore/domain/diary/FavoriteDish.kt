package app.hexaphore.domain.diary

import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.nutrition.NutrientValues

/** Identifiant d'un plat favori. UUIDv4 généré côté application, comme partout ailleurs. */
@JvmInline
value class FavoriteDishId(val value: String)

/**
 * Un plat enregistré pour être rejoué.
 *
 * **C'est le seul endroit du modèle où un nom est demandé** ([docs/07][modele]) : un
 * favori sans nom serait introuvable dans une liste, et c'est bien une liste qu'on
 * parcourt pour en choisir un. Le nom est proposé à partir des lignes — « Flocons,
 * Lait, Banane » — puis librement réécrit en « Petit-déj ».
 *
 * **Deux plats favoris ne peuvent pas porter le même nom**, à la casse et aux accents
 * près : deux « Petit-déj » dans une liste ne se distinguent plus, et choisir devient
 * un pari. L'unicité est tenue par un index sur le nom normalisé, pas seulement par
 * une vérification avant écriture.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
data class FavoriteDish(
    val id: FavoriteDishId,
    val name: String,
    val components: List<FavoriteComponent>,
    /** Combien de fois il a été rejoué. Sert au classement de la liste. */
    val useCount: Int = 0,
)

/**
 * Un composant de plat favori : de quoi reconstruire une ligne de brouillon.
 *
 * **Hybride, et c'est un arbitrage** ([D62][decisions]). [foodId] désigne une fiche
 * **vivante** quand la ligne en vient d'une : rejouer « mes flocons du matin » reflète
 * alors la fiche courante, ce que [docs/07][modele] demandait. Une ligne tapée à la
 * main n'a pas de fiche derrière elle, et ses valeurs sont donc figées ici.
 *
 * [values] est enregistré **dans les deux cas**, et c'est ce qui rend le favori
 * increvable : il sert de contenu pour une ligne sans fiche, et de repli le jour où la
 * fiche citée a été supprimée. Sans lui, un favori pourrait se retrouver à rejouer une
 * ligne sans le moindre chiffre.
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
data class FavoriteComponent(
    /** La fiche d'où vient cette ligne, ou `null` si elle a été tapée à la main. */
    val foodId: FoodId? = null,
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val grams: Double,
    /** Les valeurs telles qu'elles étaient à l'enregistrement du favori. */
    val values: NutrientValues,
)
