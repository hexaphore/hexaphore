package app.hexaphore

import android.app.Application
import app.hexaphore.domain.concurrency.DispatcherProvider
import app.hexaphore.feature.capture.sweepCapturePhotos
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Racine du graphe d'injection.
 *
 * Chaque module Gradle expose son propre module Hilt et lie ses adaptateurs aux
 * ports du domaine ; `:app` ne fait qu'assembler. C'est ce qui permet de remplacer
 * une implémentation en mémoire par Room en changeant une seule ligne.
 *
 * @see docs/06-architecture.md
 */
@HiltAndroidApp
class HexaphoreApplication : Application() {
    @Inject
    lateinit var dispatchers: DispatcherProvider

    /**
     * Le balayage des photos qu'un processus tué a laissées derrière lui.
     *
     * [docs/05][ia] le demande : le cas normal supprime le fichier dès qu'il est lu,
     * dans un `finally` ; celui-ci couvre l'anormal — l'application tuée entre le
     * déclencheur et la lecture. **Hors du fil principal**, parce qu'il touche au
     * disque, et sans rien attendre : personne ne dépend de son résultat.
     *
     * [ia]: docs/05-ia.md
     */
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch { sweepCapturePhotos(this@HexaphoreApplication) }
    }
}
