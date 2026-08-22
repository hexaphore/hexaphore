package app.hexaphore.di

import app.hexaphore.domain.food.BarcodeLookup
import app.hexaphore.domain.food.ContributionSettings
import app.hexaphore.domain.food.FoodContributionTarget
import app.hexaphore.domain.food.FoodLookup
import app.hexaphore.domain.food.FoodStore
import app.hexaphore.domain.food.ProductSource
import app.hexaphore.domain.identity.IdGenerator
import app.hexaphore.domain.usecase.LookupBarcode
import app.hexaphore.domain.usecase.OfferContribution
import app.hexaphore.domain.usecase.SaveCustomFood
import app.hexaphore.domain.usecase.SendContribution
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage du **catalogue**.
 *
 * Troisième découpage de `DomainModule`, pour la même raison que le deuxième : il
 * atteignait le seuil de fonctions de detekt, et la réponse du projet est de couper
 * selon ce que les choses sont plutôt que de relever le seuil ([docs/10][qualite]).
 *
 * La coupure existait déjà dans la lecture : `SaveCustomFood` et [LookupBarcode] ne
 * parlent ni de plats ni d'objectifs — ils écrivent et lisent des fiches. Les deux
 * arrivent d'ailleurs au même endroit, puisqu'un scan infructueux ouvre le formulaire
 * d'aliment personnel avec le code-barres déjà rempli.
 *
 * [qualite]: docs/10-qualite-et-livraison.md
 */
@Module
@InstallIn(SingletonComponent::class)
object FoodUseCaseModule {
    @Provides
    fun saveCustomFood(store: FoodStore, ids: IdGenerator): SaveCustomFood = SaveCustomFood(store, ids)

    @Provides
    fun lookupBarcode(catalogue: BarcodeLookup, products: ProductSource, store: FoodStore): LookupBarcode =
        LookupBarcode(catalogue, products, store)

    /**
     * Les deux moments de la contribution, fournis separement.
     *
     * L'un regarde s'il y a quelque chose a offrir, l'autre agit sur le monde
     * exterieur. Les fondre ferait passer une ecriture sortante pour une lecture.
     */
    @Provides
    fun offerContribution(foods: FoodLookup, settings: ContributionSettings): OfferContribution =
        OfferContribution(foods, settings)

    @Provides
    fun sendContribution(target: FoodContributionTarget, settings: ContributionSettings): SendContribution =
        SendContribution(target, settings)
}
