package app.hexaphore.data.diary

import app.hexaphore.core.database.dao.FoodCitationsDao
import app.hexaphore.domain.food.FoodCitations
import app.hexaphore.domain.food.FoodId
import javax.inject.Inject

/**
 * Le compte des citations, lu dans le journal.
 *
 * **Il vit dans `:data:diary` et non dans `:data:food`**, alors que le port est un
 * port du catalogue. C'est voulu : ce compte se dérive des entrées de journal, et
 * l'écrire ici est ce qui permet au contrat du journal de l'éprouver — noter un
 * plat, puis constater que le compte a bougé. Rangé du côté du catalogue, il aurait
 * fallu qu'un contrat du catalogue sache écrire dans le journal, ce qu'aucun de ses
 * ports ne permet ([D71][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
class RoomFoodCitations @Inject constructor(private val dao: FoodCitationsDao) : FoodCitations {
    override suspend fun count(id: FoodId): Int = dao.countFor(id.value)
}
