package app.hexavore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.theme.NeonTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * L'unique activité de l'application.
 *
 * Elle ouvre sur l'accueil. La galerie des composants existe toujours, dans une
 * activité déclarée par la seule variante `debug` : elle sert à vérifier le design
 * system sur un appareil réel, et n'a rien à faire dans un binaire de production.
 *
 * **Le thème se lit ici et nulle part ailleurs.** C'est le seul endroit qui enveloppe
 * tout ce qui s'affiche ; le poser plus bas laisserait un écran hors du réglage, et on
 * ne s'en apercevrait qu'en l'ouvrant.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val theme: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by theme.mode.collectAsStateWithLifecycle()

            // Le reglage d'Android arrive ici, et la decision est prise par le domaine :
            // « suivre le systeme » n'a alors qu'un seul endroit ou etre defini.
            NeonTheme(darkTheme = mode.isDark(isSystemInDarkTheme())) {
                HexavoreNavHost()
            }
        }
    }
}
