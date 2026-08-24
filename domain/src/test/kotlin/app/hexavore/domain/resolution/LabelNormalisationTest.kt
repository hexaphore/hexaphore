package app.hexavore.domain.resolution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L'étape 1 de [docs/04][sources], et la ligne de partage entre ses deux moitiés.
 *
 * Casse, accents et ponctuation ne sont pas éprouvés ici : ils appartiennent à
 * `SearchText`, qui a ses propres cas. Ce qui se juge ici est ce que la résolution
 * ajoute — les articles, qui partent tout de suite parce qu'une recherche
 * conjonctive ne rendrait rien avec eux, et les pluriels, qui **ne partent pas**
 * parce que l'index de l'ANSES garde les siens.
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
class LabelNormalisationTest {
    @Test
    fun `le libelle passe par la normalisation de l index`() {
        assertEquals("jus d orange", normaliseLabel("Jus d'Orange"))
    }

    @Test
    fun `un article de tete part, parce qu une recherche conjonctive ne le pardonne pas`() {
        // « du pain » ne rend rien : le catalogue compare une sous-chaine entiere, et
        // la table de l'ANSES exige que tous les termes soient presents.
        assertEquals("pain", normaliseLabel("du pain"))
        assertEquals("confiture", normaliseLabel("de la confiture"))
        assertEquals("oeuf", normaliseLabel("un œuf"))
        assertEquals("huile d olive", normaliseLabel("de l'huile d'olive"))
    }

    @Test
    fun `le meme mot reste quand il n est pas en tete`() {
        assertEquals("pain de mie", normaliseLabel("pain de mie"))
        assertEquals("blanc de poulet", normaliseLabel("blanc de poulet"))
    }

    @Test
    fun `un libelle qui n est fait que d articles ne laisse rien a chercher`() {
        assertEquals("", normaliseLabel("de la"))
    }

    @Test
    fun `la normalisation ne touche pas aux pluriels`() {
        // C'est la moitie de l'etape 1 qui n'a pas lieu ici : l'index de l'ANSES
        // garde ses pluriels, et « haricots verts » se trouve tel quel.
        assertEquals("haricots verts", normaliseLabel("des haricots verts"))
    }

    @Test
    fun `le pluriel naif retire le s et le x`() {
        assertEquals("pomme", depluralise("pommes"))
        assertEquals("chou", depluralise("choux"))
    }

    @Test
    fun `la terminaison en aux redevient al, et passe avant la regle du x`() {
        assertEquals("cheval", depluralise("chevaux"))
    }

    @Test
    fun `chaque mot est traite, pas seulement le dernier`() {
        assertEquals("haricot vert", depluralise("haricots verts"))
    }

    @Test
    fun `un mot court reste entier`() {
        // « jus » n'est pas un pluriel, et « ju » ne designerait plus rien. La regle
        // etant naive, c'est la garde qui la rend supportable — avec l'ordre des
        // requetes, qui fait que ce libelle n'arrive jamais ici.
        assertEquals("jus", depluralise("jus"))
    }
}
