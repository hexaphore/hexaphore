package app.hexaphore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hexaphore.core.designsystem.theme.NeonTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * L'unique activité de l'application.
 *
 * Elle ouvre sur l'accueil. La galerie des composants existe toujours, dans une
 * activité déclarée par la seule variante `debug` : elle sert à vérifier le design
 * system sur un appareil réel, et n'a rien à faire dans un binaire de production.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeonTheme {
                HexaphoreNavHost()
            }
        }
    }
}
