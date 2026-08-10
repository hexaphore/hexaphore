package app.hexaphore.data.food

import app.hexaphore.domain.food.FavoriteFoods
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.food.FoodSearch
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.FoodUsage
import app.hexaphore.domain.food.RecentFoods

/**
 * Les six ports du catalogue, réunis le temps d'un test.
 *
 * Le domaine les sépare exprès — un port par capacité, pour qu'un appelant ne
 * dépende que de ce qu'il utilise — et cette réunion ne remet pas ce choix en
 * cause : elle vit dans le jeu de sources de test et n'est visible d'aucun écran.
 * Le contrat, lui, porte bien sur les six à la fois, parce que c'est un même objet
 * qui les tient des deux côtés et que leurs interactions sont justement ce qui a
 * cassé — épingler touche la recherche, verser au catalogue touche les récents.
 *
 * Par délégation, sans rien demander aux implémentations : il n'y a pas de raison
 * qu'un type de production porte une interface qui n'existe que pour un test.
 */
class FoodCatalogView<T>(implementation: T) :
    FoodSearch by implementation,
    FoodLookup by implementation,
    RecentFoods by implementation,
    FavoriteFoods by implementation,
    FoodStore by implementation,
    FoodUsage by implementation
    where T : FoodSearch,
          T : FoodLookup,
          T : RecentFoods,
          T : FavoriteFoods,
          T : FoodStore,
          T : FoodUsage
