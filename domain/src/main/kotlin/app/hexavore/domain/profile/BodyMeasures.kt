package app.hexavore.domain.profile

import kotlin.math.roundToInt

/**
 * Le corps se mesure dans le système choisi, mais **il se range toujours en métrique**.
 *
 * La base porte des kilogrammes et des centimètres, et rien ici ne le change : c'est un
 * affichage et une saisie qui se convertissent, pas un stockage. Un profil exporté puis
 * restauré sur un téléphone réglé autrement décrit toujours la même personne.
 *
 * **C'est l'écart avec les quantités d'aliments**, où l'once est une unité de saisie à
 * part entière qui voyage avec la ligne. La différence n'est pas un caprice : une ligne
 * de journal est un registre de ce qui s'est passé — « j'ai mangé 6 oz » —, alors qu'un
 * poids corporel est une mesure unique dont l'unité n'appartient qu'à celui qui la lit.
 */

/** L'once avoirdupois fait exactement 0,45359237 kg pour seize d'entre elles. */
private const val KILOGRAMS_PER_POUND = 0.45359237

private const val CENTIMETRES_PER_INCH = 2.54

private const val INCHES_PER_FOOT = 12

fun kilogramsToPounds(kilograms: Double): Double = kilograms / KILOGRAMS_PER_POUND

fun poundsToKilograms(pounds: Double): Double = pounds * KILOGRAMS_PER_POUND

fun centimetresToInches(centimetres: Double): Double = centimetres / CENTIMETRES_PER_INCH

fun inchesToCentimetres(inches: Double): Double = inches * CENTIMETRES_PER_INCH

/**
 * Une taille en pieds et en pouces, telle qu'on l'énonce.
 *
 * **Deux nombres et non un**, parce que personne ne dit sa taille en pouces. C'est ce
 * qui oblige le formulaire à changer de forme selon le réglage, et c'est le prix d'une
 * saisie qu'on n'a pas à calculer soi-même.
 */
data class FeetAndInches(val feet: Int, val inches: Int)

/**
 * Des centimètres, vus en pieds et en pouces.
 *
 * **Les pouces s'arrondissent avant d'être répartis**, sans quoi 182,88 cm donnerait
 * « 5 pieds 11 pouces » au lieu de six pieds : arrondir après la division laisserait
 * douze pouces dans un pied, ce qui ne s'écrit pas.
 */
fun centimetresToFeetAndInches(centimetres: Double): FeetAndInches {
    val total = centimetresToInches(centimetres).roundToInt()
    return FeetAndInches(feet = total / INCHES_PER_FOOT, inches = total % INCHES_PER_FOOT)
}

/** Le chemin inverse. Des pouces au-delà de douze sont acceptés : ils s'additionnent. */
fun feetAndInchesToCentimetres(feet: Int, inches: Int): Double =
    inchesToCentimetres((feet * INCHES_PER_FOOT + inches).toDouble())
