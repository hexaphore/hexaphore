package app.hexaphore.domain.nutrition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'énergie déduite des macros, et les trois occasions de la proposer à tort.
 *
 * Les valeurs de référence sont celles du riz cuit et de la feta, deux fiches réelles
 * de la table de l'ANSES — la seconde parce qu'elle n'a **pas** d'énergie déterminée,
 * et que c'est précisément le cas que cette règle vient débloquer.
 */
class MacroEnergyTest {
    @Test
    fun `l energie suit les facteurs du reglement`() {
        // 180 g de riz cuit : 4 x 4,5 + 4 x 49 + 9 x 0,5 + 2 x 1,3
        val riz = NutrientValues(protein = 4.5, carbs = 49.0, fat = 0.5, fiber = 1.3)

        assertEquals(221.1, riz.macroEnergy!!, TOLERANCE)
    }

    @Test
    fun `les sucres ne comptent pas une seconde fois`() {
        val sans = NutrientValues(protein = 4.5, carbs = 49.0, fat = 0.5, fiber = 1.3)
        val avec = sans.copy(sugars = 12.0)

        // Les sucres sont **inclus** dans les glucides : les additionner en plus
        // ajouterait 48 kcal qui sont deja comptees.
        assertEquals(sans.macroEnergy, avec.macroEnergy, "les sucres sont deja dans les glucides")
    }

    @Test
    fun `les fibres comptent, a deux kilocalories le gramme`() {
        val sans = NutrientValues(protein = 0.0, carbs = 0.0, fat = 0.0, fiber = 0.0)
        val avec = sans.copy(fiber = 10.0)

        assertEquals(20.0, avec.macroEnergy!! - sans.macroEnergy!!, TOLERANCE)
    }

    @Test
    fun `une des trois exigees manque, il n y a rien a calculer`() {
        // Les glucides d'une eau-de-vie ne sont pas mesures : en supposer zero
        // fabriquerait une energie que personne n'a donnee.
        val eauDeVie = NutrientValues(protein = 0.0, fat = 0.0, fiber = 0.0)

        assertNull(eauDeVie.macroEnergy, "un inconnu ne vaut pas zero dans ce calcul")
        assertNull(eauDeVie.energyProposal)
    }

    @Test
    fun `une energie absente se propose`() {
        // La feta : 143 fiches de l'ANSES n'ont pas d'energie determinee, et la
        // ligne n'est pas enregistrable tant qu'elle en manque.
        val feta = NutrientValues(protein = 17.0, carbs = 1.0, fat = 25.0, fiber = 0.0)

        val proposition = feta.energyProposal

        assertNotNull(proposition)
        assertEquals(297.0, proposition!!.kcal, TOLERANCE)
        assertFalse(proposition.withoutFiber, "les fibres etaient mesurees, a zero")
    }

    @Test
    fun `une energie qui contredit les macros se propose`() {
        // Le cas qui a motive la demande : on corrige les proteines, l'energie reste
        // celle d'avant -- presente, donc silencieuse, et fausse.
        val corrigee = NutrientValues(kcal = 100.0, protein = 17.0, carbs = 1.0, fat = 25.0, fiber = 0.0)

        assertEquals(297.0, corrigee.energyProposal!!.kcal, TOLERANCE)
    }

    @Test
    fun `une energie coherente ne se propose pas`() {
        val feta = NutrientValues(kcal = 300.0, protein = 17.0, carbs = 1.0, fat = 25.0, fiber = 0.0)

        // 297 contre 300 : trois kilocalories d'ecart sont l'arrondi a l'entier des
        // six champs, et il n'y a rien a corriger.
        assertNull(feta.energyProposal)
    }

    @Test
    fun `un ecart en pourcentage ne suffit pas sur une petite ligne`() {
        // Une tisane a 5 kcal : 3 kcal d'ecart en font 60 %, et la proposition
        // clignoterait sur un chiffre que personne ne remarque.
        val tisane = NutrientValues(kcal = 5.0, protein = 0.0, carbs = 0.5, fat = 0.0, fiber = 0.0)

        assertNull(tisane.energyProposal, "le seuil absolu couvre l angle mort du seuil relatif")
    }

    @Test
    fun `un ecart en kilocalories ne suffit pas sur un gros plat`() {
        // 1 980 contre 2 000 : vingt kilocalories passent le seuil absolu, mais un
        // pour cent d'ecart sur une journee entiere n'est pas une contradiction.
        val journee = NutrientValues(kcal = 2000.0, protein = 100.0, carbs = 250.0, fat = 60.0, fiber = 20.0)

        assertNull(journee.energyProposal, "le seuil relatif couvre l angle mort du seuil absolu")
    }

    @Test
    fun `les deux seuils franchis, la proposition arrive`() {
        val fausse = NutrientValues(kcal = 100.0, protein = 25.0, carbs = 25.0, fat = 0.0, fiber = 0.0)

        assertEquals(200.0, fausse.energyProposal!!.kcal, TOLERANCE)
    }

    @Test
    fun `sans fibres et sans energie, on propose en le disant`() {
        // 55 fiches de l'ANSES n'ont ni energie ni fibres. Une valeur minoree qui
        // s'annonce debloque la ligne ; se taire la laisserait inenregistrable.
        val capres = NutrientValues(protein = 2.4, carbs = 0.4, fat = 0.9)

        val proposition = capres.energyProposal

        assertNotNull(proposition)
        assertTrue(proposition!!.withoutFiber, "la valeur est minoree, et l ecran doit le dire")
        assertEquals(19.3, proposition.kcal, TOLERANCE)
    }

    @Test
    fun `sans fibres mais avec une energie, on ne propose rien`() {
        // **Le garde-fou.** L'ecart peut n'etre que les fibres qu'on ignore, et
        // remplacer une mesure par un calcul minore serait une regression. Quinze
        // fiches de l'ANSES ont une energie sans fibres.
        val mesuree = NutrientValues(kcal = 350.0, protein = 17.0, carbs = 1.0, fat = 25.0)

        assertEquals(297.0, mesuree.macroEnergy!!, TOLERANCE)
        assertNull(mesuree.energyProposal, "une mesure ne se remplace pas par un calcul incomplet")
    }

    @Test
    fun `zero n est pas inconnu, et zero kilocalorie se propose`() {
        // Un cafe noir : les trois valeurs sont mesurees, et toutes nulles. Le
        // calcul rend zero, qui est la reponse juste et non une absence.
        val cafe = NutrientValues(protein = 0.0, carbs = 0.0, fat = 0.0, fiber = 0.0)

        assertEquals(0.0, cafe.energyProposal!!.kcal, TOLERANCE)
    }

    @Test
    fun `une energie a zero se corrige quand les macros disent autre chose`() {
        // Le denominateur du seuil relatif est nul : sans precaution, la comparaison
        // se serait faite contre zero pour cent et n'aurait jamais rien propose.
        val faussementVide = NutrientValues(kcal = 0.0, protein = 5.0, carbs = 5.0, fat = 0.0, fiber = 0.0)

        assertEquals(40.0, faussementVide.energyProposal!!.kcal, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
