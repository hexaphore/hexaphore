package app.hexaphore.domain.diary

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Le journal alimentaire, en lecture et en écriture.
 *
 * Port au sens de [docs/06][archi] : le domaine déclare ce dont il a besoin, et un
 * adaptateur le fournit — en mémoire pour les tests, depuis Room dans
 * l'application. Basculer de l'un à l'autre ne touche qu'une ligne du module Hilt,
 * et aucun appelant.
 *
 * L'observation rend un [Flow] — Room notifie ses invalidations, donc aucun écran
 * n'a à se rafraîchir lui-même. Les écritures sont `suspend` : un port d'écriture
 * qui rendrait un flux mélangerait commande et observation.
 *
 * **Une seule interface, et pas encore de découpage.** La ségrégation de
 * [docs/06][archi] concerne les interfaces vues par les clients ; ici, les deux
 * écrans qui s'en servent utilisent chacun la lecture et au moins une écriture.
 * Découper maintenant produirait deux interfaces implémentées par la même classe
 * pour aucun appelant allégé. Le jour où un client dépendra de méthodes qu'il
 * n'appelle pas, ce sera le signal.
 *
 * [archi]: docs/06-architecture.md
 */
interface DiaryRepository {
    /**
     * Les plats d'une journée, du plus ancien au plus récent.
     *
     * Une journée sans aucune saisie rend une **liste vide**, et non des plats à
     * zéro. Confondre les deux fausserait le calendrier et l'adaptation
     * hebdomadaire, qui doivent distinguer « rien noté » de « rien mangé ».
     */
    fun observeDay(date: LocalDate): Flow<List<Dish>>

    /** Un plat et ses lignes, ou `null` s'il n'existe plus. */
    suspend fun dish(id: DishId): Dish?

    /**
     * Écrit un plat et **remplace** entièrement ses lignes.
     *
     * Un seul verbe pour créer, modifier et restaurer, parce que les trois
     * demandent exactement la même chose de la base : que le plat et ses lignes
     * soient, après l'appel, ce que dit l'argument. Trois méthodes auraient donné
     * trois occasions d'écrire trois transactions légèrement différentes.
     *
     * L'opération est atomique : une coupure au milieu laisserait sinon un plat
     * sans contenu, visible à l'accueil et impossible à distinguer d'une saisie
     * réelle à zéro calorie.
     */
    suspend fun save(dish: Dish)

    /** Retire une ligne. Les autres lignes du plat sont intactes. */
    suspend fun deleteEntry(id: EntryId)

    /** Retire un plat et, avec lui, toutes ses lignes. */
    suspend fun deleteDish(id: DishId)
}
