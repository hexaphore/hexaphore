package app.hexavore.feature.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.DraftTextField
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.aiErrorMessage
import app.hexavore.core.designsystem.component.diagnostic
import app.hexavore.core.designsystem.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat as AndroidPermissions

/**
 * La modale « Photographier », branchée sur le graphe d'injection.
 *
 * **Elle porte tout ce qui touche à l'appareil, et rien de ce qui touche à l'IA.** Le
 * déclencheur, la galerie, la permission et la réduction vivent ici ; le `ViewModel`
 * ne reçoit qu'un tableau d'octets. C'est la division de [D66][decisions], et c'est ce
 * qui laisse une part de cet écran vérifiable sans appareil.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun PhotoRoute(
    onProposal: () -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
    viewModel: PhotoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val capture = rememberMealCapture(onJpeg = viewModel::onPhoto)

    LaunchedEffect(state.analysed) {
        if (state.analysed) {
            viewModel.onNavigated()
            onProposal()
        }
    }

    PhotoScreen(
        state = state,
        actions = PhotoActions(
            onShoot = capture.shoot,
            onPick = capture.pick,
            onNote = viewModel::onNote,
            onAnalyse = viewModel::onAnalyse,
            onCancel = viewModel::onCancel,
            onConsent = viewModel::onConsent,
            onConsentDeclined = viewModel::onConsentDeclined,
            onManual = onManual,
            onClose = onClose,
        ),
    )
}

/** Les deux façons d'obtenir une photo, et ce qu'il advient du fichier. */
@Immutable
private class MealCapture(val shoot: () -> Unit, val pick: () -> Unit)

/**
 * Tout ce qui touche à l'appareil, en un seul endroit.
 *
 * **L'appareil photo du système, pas un aperçu à nous.** [docs/02][parcours] décrit un
 * aperçu CameraX ; il demanderait une seconde implémentation — la première sert le
 * scan, qui analyse un flux en continu — pour un écran dont le seul travail est de
 * remettre un JPEG. L'appareil du système apporte sa mise au point, son flash et son
 * zoom, et il écrit directement dans notre cache.
 *
 * **La permission est quand même demandée.** Elle n'est pas nécessaire pour déléguer
 * une prise de vue — sauf quand l'application déclare `CAMERA` dans son manifeste, ce
 * que fait `:integration:scanner` pour le scan. Le système l'exige alors avant de
 * lancer l'appareil photo, et sans ce chemin le déclencheur échouerait sans rien dire.
 *
 * **Le fichier meurt avec sa lecture**, dans un `finally` : succès, échec ou annulation
 * ([docs/05][ia] § Confidentialité). Une image choisie dans la galerie, elle, n'est
 * jamais supprimée — elle ne nous appartient pas.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [ia]: docs/05-ia.md
 */
@Composable
private fun rememberMealCapture(onJpeg: (ByteArray) -> Unit): MealCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // L'adresse ou l'appareil photo du systeme ecrira. Nulle des que le fichier est
    // lu : c'est elle qui dit s'il y a quelque chose a supprimer.
    var taken by remember { mutableStateOf<Uri?>(null) }

    val reduce: (Uri) -> Unit = { uri ->
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                try {
                    reduceToJpeg(context, uri)
                } finally {
                    taken?.let { context.contentResolver.delete(it, null, null) }
                    taken = null
                }
            }
            jpeg?.let(onJpeg)
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        taken?.let { if (captured) reduce(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        picked?.let(reduce)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) taken?.let(takePicture::launch)
    }

    return remember(context) {
        MealCapture(
            shoot = {
                val uri = photoUri(context, newPhotoFile(context))
                taken = uri
                if (context.hasCameraPermission()) {
                    takePicture.launch(
                        uri,
                    )
                } else {
                    askCamera.launch(Manifest.permission.CAMERA)
                }
            },
            pick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    AndroidPermissions.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Prendre ou choisir, préciser, analyser.
 *
 * **Pas d'aperçu caméra dans l'application.** [docs/02][parcours] en décrit un ; il
 * demanderait une seconde implémentation de CameraX — la première sert le scan, qui a
 * besoin d'analyser un flux en continu — pour un écran dont le seul travail est de
 * remettre un JPEG. L'appareil photo du système le fait, avec la mise au point et le
 * flash de l'appareil, et il écrit directement dans notre cache. Le conseil de cadrage
 * que la surimpression devait porter est ici, où il se lit **avant** d'appuyer.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun PhotoScreen(state: PhotoUiState, actions: PhotoActions) {
    if (state.consentNeeded) {
        ConsentDialog(provider = state.provider, onAccept = actions.onConsent, onDecline = actions.onConsentDeclined)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.screenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.photo_close))
                }
                Text(stringResource(R.string.photo_title), style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.photo_framing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Preview(state)

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NeonButton(
                    text = stringResource(R.string.photo_shoot),
                    onClick = actions.onShoot,
                    modifier = Modifier.weight(1f),
                )
                NeonButton(
                    text = stringResource(R.string.photo_pick),
                    onClick = actions.onPick,
                    modifier = Modifier.weight(1f),
                )
            }

            DraftTextField(
                initial = state.note,
                onValueChange = actions.onNote,
                label = stringResource(R.string.photo_note_label),
                modifier = Modifier.fillMaxWidth(),
            )

            Analysis(state, actions.onAnalyse, actions.onCancel)
            Failure(state, actions.onManual)
        }
    }
}

/**
 * Ce qu'on vient de prendre, ou rien.
 *
 * La montrer n'est pas décoratif : c'est la seule façon de juger le cadrage avant de
 * payer une analyse, et c'est ce que l'aperçu caméra aurait donné.
 */
@Composable
private fun Preview(state: PhotoUiState) {
    val photo = state.photo ?: return
    val bitmap = remember(photo) {
        BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.photo_preview_a11y),
        modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Le bouton d'analyse, et **l'annulation qui coupe vraiment**.
 *
 * [docs/02][parcours] l'écrit noir sur blanc, et c'est une question d'argent autant
 * que de patience : une requête abandonnée qu'on laisse courir se paie quand même.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
private fun Analysis(state: PhotoUiState, onAnalyse: () -> Unit, onCancel: () -> Unit) {
    if (state.analysing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(Spacing.xs))
            Text(text = stringResource(R.string.photo_analysing), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel) { Text(stringResource(R.string.photo_cancel)) }
        }
        return
    }

    NeonButton(
        text = stringResource(R.string.photo_analyse),
        onClick = onAnalyse,
        modifier = Modifier.fillMaxWidth(),
        style = NeonButtonStyle.FILLED,
        availability = if (state.analysable) NeonButtonAvailability.AVAILABLE else NeonButtonAvailability.DISABLED,
    )
}

/**
 * L'échec, et la porte de sortie.
 *
 * La photo reste : réessayer ne redemande pas de ressortir le téléphone au-dessus
 * d'une assiette qu'on est peut-être en train de manger. Et la saisie manuelle est
 * offerte, parce qu'un fournisseur en panne ne doit pas empêcher de noter son repas.
 */
@Composable
private fun Failure(state: PhotoUiState, onManual: () -> Unit) {
    val error = state.error ?: return

    Text(text = aiErrorMessage(error), style = MaterialTheme.typography.bodyMedium)
    error.diagnostic?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = onManual) { Text(stringResource(R.string.photo_manual)) }
}

/**
 * Ce qui se dit **une fois**, avant que la première photo parte.
 *
 * [docs/05][ia] § Confidentialité l'exige, et exige qu'il nomme le fournisseur : « votre
 * photo part chez Mistral » se vérifie, « chez votre fournisseur » non. Refuser laisse
 * la photo en place — on peut changer d'avis sans reprendre la photo.
 *
 * [ia]: docs/05-ia.md
 */
@Composable
private fun ConsentDialog(provider: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.photo_consent_title)) },
        text = {
            Text(
                if (provider.isBlank()) {
                    stringResource(R.string.photo_consent_unnamed)
                } else {
                    stringResource(R.string.photo_consent, provider)
                },
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.photo_consent_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.photo_consent_decline)) } },
    )
}

/**
 * Les gestes de l'écran, rassemblés.
 *
 * Dix rappels passés un par un feraient une signature qu'on ne lit plus, et c'est la
 * forme que le projet a déjà retenue pour l'accueil, la validation et les réglages.
 */
@Immutable
internal data class PhotoActions(
    val onShoot: () -> Unit,
    val onPick: () -> Unit,
    val onNote: (String) -> Unit,
    val onAnalyse: () -> Unit,
    val onCancel: () -> Unit,
    val onConsent: () -> Unit,
    val onConsentDeclined: () -> Unit,
    val onManual: () -> Unit,
    val onClose: () -> Unit,
)
