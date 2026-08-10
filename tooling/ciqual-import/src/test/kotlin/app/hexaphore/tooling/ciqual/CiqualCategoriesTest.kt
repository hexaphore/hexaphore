package app.hexaphore.tooling.ciqual

import app.hexaphore.domain.food.FoodCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La table des rayons, éprouvée pour elle-même.
 *
 * Elle a la même forme de danger que [Nutrient] : elle marche parfaitement en étant
 * fausse. Un aliment rangé sous le mauvais rayon ne fait rien planter — il sort sous
 * une pastille où on ne le cherche pas, et on met des semaines à s'en apercevoir.
 */
class CiqualCategoriesTest {
    @Test
    fun `le sous-groupe l emporte sur le groupe`() {
        // Le groupe 02 melange fruits, legumes, legumineuses et oleagineux : s'arreter
        // au groupe donnerait un seul rayon pour quatre.
        assertEquals(FoodCategory.FRUITS, CiqualCategories.of("0204", "02"))
        assertEquals(FoodCategory.LEGUMES, CiqualCategories.of("0201", "02"))
        assertEquals(FoodCategory.SNACKS, CiqualCategories.of("0205", "02"))
    }

    @Test
    fun `le groupe sert de repli quand le sous-groupe est absent`() {
        // Les glaces et sorbets ont des lignes sans sous-groupe renseigne.
        assertEquals(FoodCategory.DESSERTS, CiqualCategories.of("0000", "08"))
        assertEquals(FoodCategory.DESSERTS, CiqualCategories.of(null, "08"))
    }

    @Test
    fun `ce qui n entre dans aucune case n a pas de rayon`() {
        // Et c'est un arbitrage, pas un oubli : une huile de tournesol sous
        // « Snacks » serait pire que pas de rayon du tout.
        assertNull(CiqualCategories.of("0902", "09"), "une huile n'est pas un rayon du bandeau")
        assertNull(CiqualCategories.of("1002", "10"), "un condiment ne se parcourt pas")
        assertNull(CiqualCategories.of("0103", "01"), "un plat compose n'est pas un rayon")
    }

    @Test
    fun `un code inconnu ne rend rien plutot que de deviner`() {
        assertNull(CiqualCategories.of("9999", "99"))
    }

    @Test
    fun `les huit rayons du bandeau sont tous servis`() {
        // Sans quoi une pastille resterait a l'ecran en ne rendant jamais rien --
        // ce qui se lit comme une panne, pas comme un rayon vide.
        val servis = CiqualCategories.MAPPINGS.mapNotNullTo(mutableSetOf()) { it.category }

        assertEquals(FoodCategory.entries.toSet(), servis)
    }

    @Test
    fun `chaque code n est arbitre qu une fois`() {
        // Deux rangs pour le meme code : le second serait ignore en silence, et le
        // rayon affiche ne serait pas celui qu'on lit dans la table.
        val codes = CiqualCategories.MAPPINGS.map { it.code }

        assertEquals(codes.size, codes.toSet().size, "un code apparait deux fois dans la table")
    }

    @Test
    fun `une renumerotation de l ANSES est signalee`() {
        // Le controle qui protege de l'erreur qu'aucun test ne verrait : la base se
        // genere, l'application se lance, et les poissons sont des desserts.
        val derive = CiqualCategories.drifted(mapOf("0405" to "confiseries non chocolatees"))

        assertEquals(1, derive.size)
        assertTrue(derive.single().contains("0405"), derive.single())
    }

    @Test
    fun `un intitule inchange ne declenche rien`() {
        assertTrue(CiqualCategories.drifted(mapOf("0405" to "poissons cuits")).isEmpty())
    }

    @Test
    fun `les codes de la table sont ceux de la nomenclature livree`() {
        // Une faute de frappe dans un code ne se verrait pas autrement : le rang
        // serait simplement ignore, et son rayon vide.
        assertTrue(CiqualCategories.knows("0204"))
        assertTrue(CiqualCategories.MAPPINGS.all { it.code.length == GROUP_CODE || it.code.length == SUBGROUP_CODE })
    }

    private companion object {
        const val GROUP_CODE = 2
        const val SUBGROUP_CODE = 4
    }
}
