package app.hexaphore.di

import app.hexaphore.domain.diary.DiaryRepository
import app.hexaphore.domain.time.Clock
import app.hexaphore.domain.usecase.GetDaySummary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Construction des cas d'usage.
 *
 * Ils vivent dans `:domain`, qui est du Kotlin pur et ne connaît donc pas Hilt —
 * c'est exactement la propriété qu'on veut. `:app` assemble : il sait quels ports
 * sont liés à quoi, et il monte les cas d'usage par-dessus.
 *
 * Un cas d'usage n'est pas un singleton : il ne porte aucun état, et l'instancier
 * coûte moins que de le retenir.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun getDaySummary(diary: DiaryRepository, clock: Clock): GetDaySummary = GetDaySummary(diary, clock)
}
