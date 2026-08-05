package app.hexaphore.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * La galerie des composants, accessible depuis le lanceur en variante `debug`.
 *
 * Une seconde icône plutôt qu'un écran caché derrière un geste : elle se trouve
 * sans documentation, et elle n'existe pas du tout en `release` — pas masquée,
 * absente du binaire.
 *
 * C'est l'écran qui a servi à valider l'itération 0, et il continue de servir à
 * chaque composant ajouté au design system.
 */
@AndroidEntryPoint
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryRoute()
        }
    }
}
