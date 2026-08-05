package app.hexaphore.domain.diary

import java.time.Instant
import java.time.LocalDate

/**
 * Identifiant d'un plat.
 *
 * UUIDv4 généré côté application, et non un entier auto-incrémenté : un compteur
 * rendrait toute fusion de sauvegardes impossible et interdirait des identifiants
 * stables entre appareils.
 */
@JvmInline
value class DishId(val value: String)

/**
 * Un plat : plusieurs aliments, entrés en une fois.
 *
 * C'est l'unité de saisie de l'application. Pas de petit-déjeuner, de déjeuner ni
 * de dîner : ces catégories obligeraient à ranger chaque saisie dans une case avant
 * de l'enregistrer, alors que la question qui compte est « qu'est-ce que j'ai mangé
 * aujourd'hui », pas « à quel repas ». Le classement chronologique le dit déjà, et
 * gratuitement ([D31][decisions]).
 *
 * Un plat porte **une** [source], celle par laquelle il est entré. Elle ne change
 * jamais, même après vingt corrections à la main.
 *
 * [decisions]: docs/11-decisions.md
 */
data class Dish(
    val id: DishId,
    val date: LocalDate,
    /** Origine de la saisie. Fixée à la création, jamais réécrite. */
    val source: EntrySource,
    /** Sert au classement : les plats s'affichent dans l'ordre où ils ont été notés. */
    val loggedAt: Instant,
    val entries: List<FoodEntry>,
)
