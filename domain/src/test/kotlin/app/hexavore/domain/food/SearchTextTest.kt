package app.hexavore.domain.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * La normalisation est appliquée aux deux bouts — au nom indexé et à la saisie.
 * Ces tests portent donc sur une propriété plutôt que sur une valeur : deux
 * écritures d'une même chose se rejoignent.
 */
class SearchTextTest {
    @Test
    fun `creme brulee trouve creme brulee`() {
        // Le critere de fin de tranche, ecrit dans docs/12.
        assertEquals(SearchText.normalise("Crème brûlée"), SearchText.normalise("creme brulee"))
    }

    @Test
    fun `oeuf trouve oeuf, malgre la ligature`() {
        // NFD separe une lettre de son accent, mais « œ » n'est pas un « o »
        // accentue : sans table explicite, « œuf » resterait introuvable a qui tape
        // « oeuf », c'est-a-dire a tout le monde sur un clavier mobile.
        assertEquals(SearchText.normalise("Œuf, cru"), SearchText.normalise("oeuf, cru"))
    }

    @ParameterizedTest
    @CsvSource(
        "Crème brûlée|creme brulee",
        "Pomme de terre, à l'eau|pomme de terre a l eau",
        "Bœuf, faux-filet, grillé|boeuf faux filet grille",
        "Thé infusé (aliment moyen)|the infuse aliment moyen",
        "Lait 1/2 écrémé|lait 1 2 ecreme",
        delimiter = '|',
    )
    fun `un libelle CIQUAL devient des mots`(raw: String, expected: String) {
        assertEquals(expected, SearchText.normalise(raw))
    }

    @Test
    fun `la ponctuation coupe les mots au lieu de les coller`() {
        // Sans cette regle, « faux-filet » deviendrait « fauxfilet » et ne se
        // trouverait ni par « faux » ni par « filet ».
        assertEquals("faux filet", SearchText.normalise("faux-filet"))
    }

    @Test
    fun `les chiffres restent`() {
        assertEquals("omega 3", SearchText.normalise("Oméga 3"))
    }

    @Test
    fun `une saisie deja normalisee ne bouge plus`() {
        // La fonction est idempotente : l'appliquer deux fois ne peut pas separer
        // ce qu'un premier passage avait rapproche.
        val once = SearchText.normalise("Crème brûlée, à l'ancienne")
        assertEquals(once, SearchText.normalise(once))
    }
}
