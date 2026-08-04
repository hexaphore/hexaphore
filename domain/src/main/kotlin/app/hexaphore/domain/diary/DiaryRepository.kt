package app.hexaphore.domain.diary

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Un repas et les lignes qu'il contient.
 *
 * Regroupés ici parce qu'ils sont toujours lus ensemble : l'accueil affiche des
 * repas, jamais des lignes en vrac.
 */
data class LoggedMeal(val meal: Meal, val entries: List<FoodEntry>)

/**
 * Lecture du journal alimentaire.
 *
 * Port au sens de [docs/06][archi] : le domaine déclare ce dont il a besoin, et un
 * adaptateur le fournit — en mémoire aujourd'hui, depuis Room ensuite. Basculer de
 * l'un à l'autre ne doit toucher qu'une ligne du module Hilt, et aucun appelant.
 *
 * La lecture rend un [Flow] : la base notifie ses invalidations, et aucun écran n'a
 * à se rafraîchir lui-même. Les écritures, elles, seront des `suspend` — un port
 * d'écriture qui rendrait un flux mélangerait commande et observation.
 *
 * Volontairement étroit. Un `DiaryRepository` fourre-tout obligerait le test de
 * l'accueil à fabriquer un faux à quinze méthodes dont il n'en appelle qu'une.
 *
 * [archi]: docs/06-architecture.md
 */
interface DiaryRepository {
    /**
     * Les repas d'une journée, dans l'ordre d'affichage.
     *
     * Une journée sans aucune saisie rend une **liste vide**, et non des repas à
     * zéro. Confondre les deux fausserait le calendrier et l'adaptation
     * hebdomadaire, qui doivent distinguer « rien noté » de « rien mangé ».
     */
    fun observeDay(date: LocalDate): Flow<List<LoggedMeal>>
}
