package app.hexavore.data.food

import app.hexavore.domain.food.BarcodeLookup
import app.hexavore.domain.food.FavoriteFoods
import app.hexavore.domain.food.FoodLookup
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.food.FoodStore
import app.hexavore.domain.food.FoodUsage
import app.hexavore.domain.food.RecentFoods

/**
 * Les sept ports du catalogue, réunis le temps d'un test.
 *
 * [barcodes] arrive à part parce qu'il vient d'un **second** adaptateur : la lecture
 * par code-barres est la seule qui ait à savoir que `source_ref` range deux espaces de
 * noms dans une même colonne, et elle a sa propre classe des deux côtés. Le contrat,
 * lui, la joue avec les six autres — c'est bien le même catalogue qu'elles lisent.
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
class FoodCatalogView<T>(implementation: T, barcodes: BarcodeLookup) :
    FoodSearch by implementation,
    FoodLookup by implementation,
    BarcodeLookup by barcodes,
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
