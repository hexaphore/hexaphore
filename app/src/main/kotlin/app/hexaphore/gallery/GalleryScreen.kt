package app.hexaphore.gallery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.hexaphore.R
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.theme.NeonTheme
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.nutrition.Macro
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * La galerie, branchée sur le graphe d'injection.
 *
 * La bascule de thème est locale à cet écran : elle sert à vérifier les deux thèmes
 * sur un appareil sans passer par les réglages système. Le vrai réglage d'apparence
 * arrivera avec l'écran de réglages.
 */
@Composable
fun GalleryRoute(viewModel: GalleryViewModel = hiltViewModel()) {
    val systemDark = isSystemInDarkTheme()
    var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

    NeonTheme(darkTheme = darkTheme) {
        GalleryScreen(
            today = viewModel.today,
            darkTheme = darkTheme,
            onToggleTheme = { darkTheme = !darkTheme },
        )
    }
}

/**
 * La galerie elle-même, sans état.
 *
 * Sans état pour la même raison que tous les écrans du projet : elle reçoit ce
 * qu'elle affiche et émet ce qui se passe, donc elle se teste et s'affiche en
 * aperçu sans graphe d'injection.
 */
@Composable
fun GalleryScreen(today: LocalDate, darkTheme: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            GalleryHeader(today = today, darkTheme = darkTheme, onToggleTheme = onToggleTheme)
            PaletteSection()
            RingSection()
            BarSection()
            ButtonSection()
            BadgeSection()
            TypographySection()
        }
    }
}

@Composable
private fun GalleryHeader(today: LocalDate, darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.gallery_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.gallery_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Cette date ne peut venir que du port Clock, résolu par Hilt : c'est la
        // preuve visible que la chaîne d'injection tient de bout en bout.
        Text(
            text = stringResource(R.string.gallery_today, formatter.format(today)),
            style = MaterialTheme.typography.labelLarge,
            color = NeonTheme.macros[Macro.CALORIES].base,
        )
        NeonButton(
            text =
            stringResource(
                if (darkTheme) R.string.gallery_theme_light else R.string.gallery_theme_dark,
            ),
            onClick = onToggleTheme,
        )
    }
}
