package app.hexavore.domain.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Le corps se mesure autrement, et se range toujours pareil.
 *
 * **Ces conversions ne servent qu'à l'affichage et à la saisie.** La base garde des
 * kilogrammes et des centimètres, donc basculer de réglage ne réécrit rien — c'est ce
 * qui permet de regarder ses livres, puis de revenir, sans qu'un chiffre ait bougé.
 */
class BodyMeasuresTest {
    @Test
    fun `une livre vaut la definition legale, pas un arrondi`() {
        // 0,45359237 exactement : arrondir a 0,4536 deplacerait un poids de 80 kg de
        // sept grammes, ce qui se voit sur une courbe lissee.
        assertEquals(176.3698097, kilogramsToPounds(80.0), TOLERANCE)
        assertEquals(80.0, poundsToKilograms(kilogramsToPounds(80.0)), TOLERANCE)
    }

    @Test
    fun `un pouce vaut deux virgule cinquante-quatre centimetres`() {
        assertEquals(70.0, centimetresToInches(177.8), TOLERANCE)
        assertEquals(177.8, inchesToCentimetres(70.0), TOLERANCE)
    }

    @Test
    fun `six pieds ne s ecrivent jamais cinq pieds douze pouces`() {
        // Le piege : arrondir apres avoir divise laisserait douze pouces dans un pied.
        // Les pouces s arrondissent donc avant d etre repartis.
        assertEquals(FeetAndInches(feet = 6, inches = 0), centimetresToFeetAndInches(182.88))
        assertEquals(FeetAndInches(feet = 5, inches = 11), centimetresToFeetAndInches(180.0))
    }

    @Test
    fun `une taille se relit dans les deux sens`() {
        val taille = centimetresToFeetAndInches(177.8)

        assertEquals(FeetAndInches(feet = 5, inches = 10), taille)
        assertEquals(177.8, feetAndInchesToCentimetres(taille.feet, taille.inches), TOLERANCE)
    }

    @Test
    fun `des pouces au-dela de douze s additionnent`() {
        // Personne ne tape « 4 pieds 22 pouces », mais un champ le permet et le refuser
        // demanderait de dire pourquoi. S additionner est la lecture evidente.
        assertEquals(
            feetAndInchesToCentimetres(feet = 5, inches = 10),
            feetAndInchesToCentimetres(feet = 4, inches = 22),
            TOLERANCE,
        )
    }

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
