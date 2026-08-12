package app.hexaphore.di

import app.hexaphore.BuildConfig
import app.hexaphore.integration.openfoodfacts.ClientIdentity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * La seule chose que `:app` a à dire au client d'Open Food Facts : qui l'appelle.
 *
 * **C'est ici et non dans le module d'intégration**, parce que le numéro qu'exige
 * [D26][decisions] est celui du **binaire** — `versionName`, suffixe de variante
 * compris. Un module de bibliothèque qui le relirait dans le catalogue de versions
 * donnerait un second endroit à tenir d'accord, et se tromperait d'un cran le jour
 * où une variante en aura un autre.
 *
 * L'adresse est celle de l'organisation GitHub et non d'un compte personnel : elle
 * survit à un changement de propriétaire, à un passage en association et à un départ
 * de son auteur ([D14][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object OpenFoodFactsIdentityModule {
    @Provides
    @Singleton
    fun identity(): ClientIdentity =
        ClientIdentity("Hexaphore/${BuildConfig.VERSION_NAME} (github.com/hexaphore/hexaphore)")
}
