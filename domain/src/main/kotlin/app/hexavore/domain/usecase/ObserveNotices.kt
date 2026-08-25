package app.hexavore.domain.usecase

import app.hexavore.domain.ai.AiCredentials
import app.hexavore.domain.ai.activeConfiguration
import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.notice.KeyRejection
import app.hexavore.domain.notice.Notice
import app.hexavore.domain.notice.NoticeSettings
import app.hexavore.domain.notice.WEIGHT_SILENCE_DAYS
import app.hexavore.domain.profile.WeightEntry
import app.hexavore.domain.profile.WeightLog
import app.hexavore.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Ce qui mérite une pastille, en ce moment.
 *
 * **Un seul endroit décide.** Quatre règles réparties dans les quatre écrans qui les
 * affichent auraient divergé au premier changement — et surtout, chacune aurait été
 * évaluée là où elle se voit, donc jamais là où elle ne se voit pas. C'est un flux
 * unique que tous les écrans lisent.
 *
 * **Chaque règle est éteinte par son réglage avant d'être évaluée**, et non après :
 * une pastille désactivée ne doit pas coûter la lecture qui la produirait.
 *
 * ### Les quatre règles, et pourquoi elles s'éteignent toutes seules
 *
 * Aucune ne se rejette. Chacune décrit une **situation**, pas un message : elle
 * disparaît quand la situation cesse, et un bouton « je sais » n'aurait fait que
 * permettre de mentir à l'application.
 *
 * - Pas de fournisseur qui serve → une clé enregistrée l'éteint.
 * - Clé refusée au dernier appel → une analyse qui aboutit l'éteint.
 * - Pas de pesée depuis [WEIGHT_SILENCE_DAYS] jours → une pesée l'éteint.
 * - Hier sans aucune ligne → une ligne notée sur hier l'éteint, et **le lendemain la
 *   déplace** plutôt que de l'accumuler : c'est toujours la veille du jour courant qui
 *   est regardée, jamais une liste de jours oubliés.
 *
 * @see docs/02-parcours-et-ecrans.md
 */
class ObserveNotices(
    private val settings: NoticeSettings,
    private val credentials: AiCredentials,
    private val rejection: KeyRejection,
    private val weights: WeightLog,
    private val diary: DiaryRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<Set<Notice>> = combine(
        settings.observe(),
        credentials.observe(),
        rejection.observe(),
        weights.observeLatest(),
        diary.observeDay(clock.today().minusDays(1)),
    ) { allumees, setup, refusee, pesee, hier ->
        buildSet {
            if (setup.activeConfiguration() == null) add(Notice.AI_NOT_CONFIGURED)
            // **Une clé refusée n'a de sens que s'il y en a une.** Sans fournisseur
            // actif, les deux pastilles s'allumeraient ensemble et diraient la même
            // chose deux fois -- alors que le geste a faire est le meme.
            if (refusee && setup.activeConfiguration() != null) add(Notice.AI_KEY_REJECTED)
            if (pesee.isStale(clock.today())) add(Notice.WEIGHT_STALE)
            if (hier.isEmpty()) add(Notice.YESTERDAY_EMPTY)
        }.intersect(allumees)
    }
}

/**
 * La pesée est-elle trop vieille — ou absente ?
 *
 * **Absente compte comme trop vieille.** Quelqu'un qui n'a jamais noté de poids est
 * précisément celui à qui la courbe et l'adaptation ne servent à rien, et attendre une
 * première pesée pour réclamer une pesée serait un silence qui ne se romprait jamais.
 *
 * La comparaison porte sur des **jours** et non sur des instants : une pesée du matin
 * et une pesée du soir sont toutes deux « d'aujourd'hui », et le contraire ferait
 * apparaître la pastille à des heures qui n'ont aucun sens.
 */
private fun WeightEntry?.isStale(today: LocalDate): Boolean =
    this == null || ChronoUnit.DAYS.between(date, today) >= WEIGHT_SILENCE_DAYS
