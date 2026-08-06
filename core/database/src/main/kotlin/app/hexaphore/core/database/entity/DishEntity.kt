package app.hexaphore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un plat : plusieurs aliments, entrés en une fois.
 *
 * `source` n'est **jamais réécrite**. Un plat reste éditable à la main
 * indéfiniment ; son origine est un fait historique, pas un état. Corriger une
 * quantité sur une proposition de l'IA ne doit pas la faire passer pour une saisie
 * manuelle — ce serait perdre la seule trace de ce qui a été deviné.
 *
 * `date` est la journée **locale** à laquelle le plat est rattaché, en ISO-8601 :
 * triable en SQL, lisible à l'œil dans un export. `loggedAt` est l'instant en
 * millisecondes UTC, et sert au classement à l'intérieur d'une journée.
 *
 * @see docs/07-modele-de-donnees.md
 * @see docs/11-decisions.md — D31, D32
 */
@Entity(
    tableName = "dish",
    // L'accueil lit une journée et l'affiche dans l'ordre : c'est l'index qui rend
    // cette lecture immédiate, et c'est la requête la plus fréquente de l'application.
    indices = [Index(value = ["date", "logged_at"])],
)
data class DishEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "logged_at")
    val loggedAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
