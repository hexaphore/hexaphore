package app.hexaphore.data.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexaphore.core.database.HexaphoreDatabase
import app.hexaphore.core.database.ciqual.CiqualDatabase
import app.hexaphore.core.testing.FixedClock
import app.hexaphore.core.testing.SequentialIdGenerator
import app.hexaphore.core.testing.TestDispatchers
import app.hexaphore.domain.food.Food
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le contrat du catalogue, joué sur Room et sur la table de l'ANSES **livrée**.
 *
 * C'est le côté qui manquait. Les tests de la tranche 3 ne connaissaient que le faux,
 * qui rendait des fiches déjà écrites ; celui-ci fabrique des fiches qui ne sont pas
 * encore au catalogue, avec des identifiants provisoires — et c'est exactement là que
 * deux défauts sont passés ([D53][decisions]).
 *
 * Sous Robolectric, donc sans appareil : un test qu'il faut brancher un téléphone
 * pour exécuter est un test qu'on n'exécute pas ([D35][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RoomFoodCatalogTest : FoodCatalogContract() {
    private val bases = mutableListOf<HexaphoreDatabase>()

    @After
    fun fermer() = bases.forEach { it.close() }

    override fun catalogue(stored: List<Food>, reference: List<Food>): FoodCatalogView<*> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = Room
            .inMemoryDatabaseBuilder(context, HexaphoreDatabase::class.java)
            .build()
            .also(bases::add)

        val ciqual = CiqualDatabase(context)
        reference.forEach { ciqual.verifier(it) }

        val catalogue = RoomFoodCatalog(
            dao = base.foodDao(),
            marks = base.foodMarksDao(),
            ciqual = ciqual,
            ids = SequentialIdGenerator("provisoire"),
            clock = FixedClock(MAINTENANT),
            dispatchers = TestDispatchers(Dispatchers.IO),
        )
        runBlocking { stored.forEach { catalogue.save(it) } }
        return FoodCatalogView(catalogue, RoomBarcodeLookup(base.foodDao(), TestDispatchers(Dispatchers.IO)))
    }

    /**
     * La table de l'ANSES n'est pas garnissable : elle est livrée telle quelle.
     *
     * Ce contrôle est ce qui empêche le contrat de ne rien éprouver de ce côté. Sans
     * lui, une fiche de référence absente de la base rendrait simplement zéro
     * résultat, et la moitié des cas passeraient en ne mesurant rien — le genre de
     * vert qui a déjà coûté deux corrections.
     */
    private fun CiqualDatabase.verifier(attendu: Food) {
        val code = checkNotNull(attendu.sourceRef) { "une fiche de reference porte un code CIQUAL" }
        val ligne = byCode(code)
        checkNotNull(ligne) { "le code $code n'est plus dans la table livree : la fixture est perimee" }
        check(ligne.name == attendu.name) {
            "l intitule du code $code a change : attendu [${attendu.name}], lu [${ligne.name}]"
        }
    }

    @Test
    fun `la table livree contient bien ce que le contrat lui demande`() {
        // Une sonde, pas un doublon : elle nomme la seule facon dont ce fichier peut
        // devenir un decor vide, et elle echoue avec le code fautif.
        catalogue(reference = listOf(POMME_DE_REFERENCE, POIRE_DE_REFERENCE, CREME_BRULEE))
    }

    /**
     * Le titre court, depuis le CSV versionné jusqu'au modèle de domaine.
     *
     * **La couture entière**, et c'est là qu'elle se romprait sans qu'un test de
     * module ne le voie : `short-names.csv` est lu par une tâche Gradle, écrit dans
     * une colonne de `ciqual.db`, relu par `CiqualDatabase`, puis porté par `Food`.
     * Chaque morceau a son test ; celui-ci est le seul qui parte du fichier livré.
     *
     * Le code choisi est l'une des six lignes écrites à la main, donc il survit à la
     * génération — qui ne redemande que les codes absents du fichier.
     */
    @Test
    fun `un titre court du fichier livre remonte jusqu au modele`() {
        val ligne = CiqualDatabase(ApplicationProvider.getApplicationContext()).byCode(CODE_AVEC_TITRE)

        checkNotNull(ligne) { "le code $CODE_AVEC_TITRE n'est plus dans la table livree : la fixture est perimee" }
        assertEquals("Cuisse de poulet rotie", ligne.shortName)
        assertEquals("le libelle d'origine ne bouge pas", "Poulet, cuisse, viande rôtie/cuite au four", ligne.name)
    }

    /**
     * Un libellé déjà lisible n'a pas de titre court, et ce n'est pas un trou.
     *
     * Sans ce cas, une lecture qui rendrait toujours la même chaîne — le libellé, par
     * exemple — passerait le test précédent sans rien mesurer.
     */
    @Test
    fun `une fiche sans titre court en rend aucun`() {
        val ligne = CiqualDatabase(ApplicationProvider.getApplicationContext()).byCode(CODE_SANS_TITRE)

        checkNotNull(ligne) { "le code $CODE_SANS_TITRE n'est plus dans la table livree : la fixture est perimee" }
        assertNull(ligne.shortName)
    }

    private companion object {
        val MAINTENANT: Instant = Instant.parse("2026-08-10T10:00:00Z")

        /** « Poulet, cuisse, viande rôtie/cuite au four », l'une des six lignes écrites à la main. */
        const val CODE_AVEC_TITRE = "36006"

        /** « Bigorneau, cru » : quatorze caractères, il n'y a rien à raccourcir. */
        const val CODE_SANS_TITRE = "20009"
    }
}

/**
 * Version d'Android simulée par Robolectric.
 *
 * Choisie parmi celles que la version de Robolectric embarque, et non alignée sur
 * `compileSdk` : ces tests portent sur SQLite et sur des flux, pas sur une API dont
 * la version importerait.
 */
internal const val ROBOLECTRIC_SDK = 33
