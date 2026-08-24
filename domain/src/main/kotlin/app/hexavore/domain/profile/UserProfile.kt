package app.hexavore.domain.profile

import java.time.LocalDate
import java.time.Period

/**
 * Le sexe, tel que la formule de Mifflin-St Jeor le demande.
 *
 * Trois valeurs pour deux variantes de formule : [UNSPECIFIED] applique la **moyenne
 * des deux**, soit un terme constant à −78. Ce n'est pas un repli technique, c'est une
 * réponse — l'écran d'accueil du profil le dit en toutes lettres, parce qu'un chiffre
 * inexpliqué est un chiffre auquel on ne fait pas confiance ([docs/02][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
enum class Sex(val bmrConstant: Double) {
    MALE(MALE_CONSTANT),
    FEMALE(FEMALE_CONSTANT),
    UNSPECIFIED((MALE_CONSTANT + FEMALE_CONSTANT) / 2),
}

private const val MALE_CONSTANT = 5.0
private const val FEMALE_CONSTANT = -161.0

/**
 * Le niveau d'activité, décrit par un exemple concret et non par un adjectif.
 *
 * « Modérément actif » ne veut rien dire ; « sport 3 à 5 fois par semaine » se répond
 * en une seconde. Les libellés vivent dans les ressources d'un écran — `:domain` ne
 * connaît pas Android — mais le **facteur** est une règle nutritionnelle et il est ici.
 *
 * **L'exercice est intégré au multiplicateur, jamais ajouté au jour le jour.** Un
 * utilisateur qui note ses séances finit par « manger ses calories brûlées » : les
 * montres surévaluent la dépense de 20 à 90 %. Un multiplicateur stable est moins
 * précis un jour donné, plus juste sur un mois ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(SEDENTARY_FACTOR),
    LIGHT(LIGHT_FACTOR),
    MODERATE(MODERATE_FACTOR),
    ACTIVE(ACTIVE_FACTOR),
    VERY_ACTIVE(VERY_ACTIVE_FACTOR),
}

// Les cinq facteurs de docs/03. Nommes un par un plutot que poses dans le
// constructeur : ils sont la seule chose de ce fichier qu'une relecture de la
// litterature pourrait faire bouger, et ils doivent se retrouver d'un coup d'oeil.
private const val SEDENTARY_FACTOR = 1.20
private const val LIGHT_FACTOR = 1.375
private const val MODERATE_FACTOR = 1.55
private const val ACTIVE_FACTOR = 1.725
private const val VERY_ACTIVE_FACTOR = 1.90

/** Ce que l'affichage montre. Le stockage est **toujours** métrique ([docs/07][modele]). */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

/**
 * Qui utilise l'application, pour ce que le calcul en a besoin.
 *
 * **La date de naissance est stockée, jamais l'âge.** Un âge se périme en silence :
 * l'objectif calculé un 3 janvier resterait celui d'une personne d'un an plus jeune,
 * indéfiniment, et rien dans l'interface ne le dirait.
 *
 * Le poids n'est pas ici : il vit dans le journal de pesées, parce qu'il change et
 * qu'on veut sa tendance ([docs/07][modele]).
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
data class UserProfile(
    val birthDate: LocalDate,
    val sex: Sex,
    val heightCm: Double,
    val activityLevel: ActivityLevel,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    /**
     * L'âge **à une date donnée**, et non « aujourd'hui ».
     *
     * La date vient de l'appelant, qui la tient d'une [app.hexavore.domain.time.Clock]
     * injectée : c'est ce qui rend le calcul reproductible, et c'est aussi ce qui
     * permettra de rejuger une journée passée avec l'âge qu'on avait ce jour-là.
     */
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years
}
