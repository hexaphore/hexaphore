package app.hexaphore.integration.scanner

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.hexaphore.domain.food.Barcode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors

/**
 * L'aperçu caméra, et le décodage qui tourne dessus.
 *
 * **Ce module porte une composable, ce qu'aucun autre `:integration` ne fait.** Une
 * caméra n'est pas une source de données qu'on puisse mettre derrière un port : c'est
 * une **surface**, et l'abstraire demanderait au domaine de connaître un type de vue —
 * exactement ce que l'architecture lui interdit. Le module fournit donc la surface et
 * le décodage ; l'écran qui l'utilise garde la permission, les états et la navigation.
 *
 * [onBarcode] n'est appelé qu'après deux lectures d'accord ([SteadyBarcode]), et une
 * seule fois : c'est [resumeKey] qui rouvre la lecture. Un compteur plutôt qu'un
 * booléen, parce que rescanner le **même** produit doit remarcher, et qu'un booléen
 * repassé à la même valeur ne relance rien.
 *
 * **Rien n'est vérifiable ici sans appareil.** Le liage CameraX, la rotation de
 * l'image et la torche ne s'éprouvent pas sur la JVM ; la règle qui pouvait l'être a
 * été sortie dans [SteadyBarcode].
 *
 * @see docs/02-parcours-et-ecrans.md
 */
@Composable
fun BarcodeCamera(
    onBarcode: (Barcode) -> Unit,
    modifier: Modifier = Modifier,
    torchOn: Boolean = false,
    resumeKey: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val steady = remember { SteadyBarcode() }
    // Sans cette indirection, la lambda capturee a la premiere composition serait
    // celle que l'analyseur appellerait pour toujours.
    val latest by rememberUpdatedState(onBarcode)

    val preview = remember { PreviewView(context) }
    // Un seul fil pour l'analyse : ML Kit y decode une image a la fois, et c'est
    // suffisant -- CameraX jette les images en trop plutot que de les empiler.
    val analysisThread = remember { Executors.newSingleThreadExecutor() }
    val camera = remember { CameraHolder() }

    LaunchedEffect(resumeKey) { steady.resume() }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).await(context)
        val analysis = ImageAnalysis
            .Builder()
            // Le scan lit le present : une file d'images en retard ferait decoder un
            // cadrage qu'on a deja quitte.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisThread, BarcodeAnalyzer(steady) { latest(it) }) }

        provider.unbindAll()
        camera.bound = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            Preview.Builder().build().apply { surfaceProvider = preview.surfaceProvider },
            analysis,
        )
    }

    LaunchedEffect(torchOn) { camera.bound?.cameraControl?.enableTorch(torchOn) }

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            analysisThread.shutdown()
        }
    }

    AndroidView(factory = { preview }, modifier = modifier)
}

/** Ce que le liage rend, retenu pour piloter la torche. */
private class CameraHolder {
    var bound: Camera? = null
}

/**
 * L'attente du fournisseur, en suspension plutôt qu'en blocage.
 *
 * `ProcessCameraProvider.getInstance` rend un `ListenableFuture` ; l'attendre avec
 * `get()` immobiliserait le fil principal le temps que la caméra s'initialise, ce qui
 * se voit à l'ouverture de l'écran.
 */
private suspend fun ListenableFuture<ProcessCameraProvider>.await(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener({ continuation.resumeWith(runCatching { get() }) }, ContextCompat.getMainExecutor(context))
    }
