package app.hexavore.domain.food

import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.nutrition.NutrientValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Le nom sous lequel une fiche se montre, et celui qu'elle ne perd jamais.
 *
 * Un seul endroit décide — [Food.displayName] — parce que quatre listes, l'écran de
 * validation et le nom que prend une nouvelle ligne posent la même question. Six
 * réponses divergeraient au premier oubli.
 */
class FoodDisplayNameTest {
    @Test
    fun `le titre court l emporte quand la fiche en a un`() {
        assertEquals("Cuisse de poulet rotie", POULET.displayName)
    }

    @Test
    fun `sans titre court, c est le libelle qui s affiche`() {
        // Deux cinquiemes de la table sont dans ce cas, et tous les aliments
        // personnels : l'absence de titre est la normale, pas un trou.
        assertEquals(POULET.name, POULET.copy(shortName = null).displayName)
    }

    @Test
    fun `le libelle d origine ne bouge jamais`() {
        // C'est lui qui relie la fiche a sa source, et c'est sur lui que la
        // recherche compare : un index bati sur le titre court ne trouverait plus
        // « poulet cuit au four ».
        assertEquals("Poulet, cuisse, viande rotie/cuite au four", POULET.name)
    }

    @Test
    fun `une ligne neuve prend le titre court`() {
        // C'est ce nom-la que le journal figera, et l'accueil est l'ecran ou un
        // libelle a rallonge se paie tous les jours.
        val ligne = DraftLine.of(DraftLineId("l1"), POULET)

        assertEquals("Cuisse de poulet rotie", ligne.name)
    }

    @Test
    fun `une ligne neuve garde la fiche, donc le libelle reste atteignable`() {
        // L'ecran de validation le rappelle sous le champ : c'est lui qui distingue
        // deux preparations du meme aliment, et le titre court en abandonne une part.
        val ligne = DraftLine.of(DraftLineId("l1"), POULET)

        assertEquals(POULET.name, ligne.food?.name)
    }

    private companion object {
        val POULET = Food(
            id = FoodId("f-poulet"),
            source = FoodSource.CIQUAL,
            sourceRef = "36006",
            name = "Poulet, cuisse, viande rotie/cuite au four",
            shortName = "Cuisse de poulet rotie",
            per100g = NutrientValues(kcal = 185.0, protein = 27.0),
        )
    }
}
