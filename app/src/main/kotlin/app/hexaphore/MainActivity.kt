package app.hexaphore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hexaphore.gallery.GalleryRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * L'unique activité de l'application.
 *
 * Elle n'affiche pour l'instant que la galerie des composants : l'itération 0 ne
 * livre aucune fonctionnalité, seulement le socle qu'on ne peut pas rétro-installer.
 * L'accueil réel prendra sa place en tranche 1.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryRoute()
        }
    }
}
