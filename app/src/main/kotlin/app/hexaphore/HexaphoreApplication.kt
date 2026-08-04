package app.hexaphore

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

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
class HexaphoreApplication : Application()
