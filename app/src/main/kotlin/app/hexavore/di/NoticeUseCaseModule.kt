package app.hexavore.di

import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.notice.KeyRejection
import app.hexavore.domain.notice.NoticeSettings
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.ObserveNotices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Ce qui merite une pastille, construit une fois.
 *
 * Un module a part : les pastilles lisent cinq sources qui n'ont rien a voir entre
 * elles -- des cles, un refus, des pesees, le journal, l'horloge -- et les poser
 * ailleurs aurait fait dependre un module de sujets qui ne le concernent pas.
 */
@Module
@InstallIn(SingletonComponent::class)
object NoticeUseCaseModule {
    @Provides
    fun observeNotices(
        settings: NoticeSettings,
        credentials: AiCredentials,
        rejection: KeyRejection,
        weights: WeightLog,
        diary: DiaryRepository,
        clock: Clock,
    ): ObserveNotices = ObserveNotices(settings, credentials, rejection, weights, diary, clock)
}
