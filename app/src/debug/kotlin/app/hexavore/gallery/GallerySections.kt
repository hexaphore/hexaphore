package app.hexavore.gallery

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.R
import app.hexavore.core.designsystem.component.MacroBar
import app.hexavore.core.designsystem.component.MacroRing
import app.hexavore.core.designsystem.component.MacroRingDefaults
import app.hexavore.core.designsystem.component.MacroUnit
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonAvailability
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.SourceBadge
import app.hexavore.core.designsystem.theme.NeonTheme
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.nutrition.Macro

// Les sections de la galerie. Les libellés y sont écrits en clair, contrairement au
// reste de l'application : ce sont des échantillons de démonstration, et les faire
// passer par des ressources reviendrait à les donner à traduire.

@Composable
internal fun GallerySection(@StringRes titleRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        content()
    }
}

@Composable
internal fun PaletteSection() = GallerySection(R.string.gallery_section_palette) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Macro.entries.forEach { macro -> PaletteRow(macro) }
    }
}

@Composable
private fun PaletteRow(macro: Macro) {
    val palette = NeonTheme.macros[macro]

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(macro.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = palette.base,
            modifier = Modifier.weight(1f),
        )
        Swatch(color = palette.base, label = stringResource(R.string.gallery_swatch_base))
        Swatch(color = palette.glow, label = stringResource(R.string.gallery_swatch_glow))
        Swatch(color = palette.muted, label = stringResource(R.string.gallery_swatch_muted))
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    val shape = RoundedCornerShape(Radius.field)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
            Modifier
                .size(SwatchSize)
                .background(color, shape)
                // Une bordure, sans quoi la déclinaison « lueur » du thème
                // clair, qui est transparente, serait invisible plutôt que
                // parlante.
                .border(SwatchBorder, MaterialTheme.colorScheme.outline, shape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RingSection() = GallerySection(R.string.gallery_section_ring) {
    var progress by remember { mutableFloatStateOf(RING_SAMPLE_PROGRESS) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MacroRing(
                macro = Macro.CALORIES,
                progress = progress,
                contentDescription = stringResource(R.string.gallery_ring_a11y),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.gallery_ring_remaining),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.gallery_ring_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MacroRing(
                    macro = Macro.PROTEIN,
                    progress = progress,
                    diameter = MacroRingDefaults.CalendarDiameter,
                )
                MacroRing(
                    macro = Macro.FIBER,
                    progress = progress,
                    diameter = MacroRingDefaults.MonthDiameter,
                    strokeWidth = SmallRingStroke,
                )
            }
        }
        // Sert aussi à vérifier le respect du réglage « animations réduites » :
        // avec ce réglage actif, la valeur saute au lieu de glisser.
        NeonButton(
            text = stringResource(R.string.gallery_animate),
            onClick = {
                progress = if (progress > 1f) RING_SAMPLE_PROGRESS else RING_OVERSHOOT_PROGRESS
            },
            macro = Macro.PROTEIN,
        )
    }
}

@Composable
internal fun BarSection() = GallerySection(R.string.gallery_section_bar) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SubHeading(R.string.gallery_bar_targets)
        MacroBar(Macro.PROTEIN, stringResource(R.string.macro_protein), consumed = 87f, goal = 144f)
        MacroBar(Macro.FIBER, stringResource(R.string.macro_fiber), consumed = 12f, goal = 35f)

        SubHeading(R.string.gallery_bar_limits)
        MacroBar(Macro.CARBS, stringResource(R.string.macro_carbs), consumed = 240f, goal = 312f)
        MacroBar(Macro.SUGARS, stringResource(R.string.macro_sugars), consumed = 41f, goal = 63f)
        MacroBar(Macro.SUGARS, stringResource(R.string.macro_sugars), consumed = 78f, goal = 63f)
        MacroBar(Macro.FAT, stringResource(R.string.macro_fat), consumed = 52f, goal = 70f)

        SubHeading(R.string.gallery_bar_targets)
        MacroBar(
            macro = Macro.CALORIES,
            label = stringResource(R.string.macro_calories),
            consumed = 1220f,
            goal = 2000f,
            unit = MacroUnit.KCAL,
        )
    }
}

@Composable
internal fun ButtonSection() = GallerySection(R.string.gallery_section_button) {
    var explanation by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NeonButton(text = "Enregistrer", onClick = {}, style = NeonButtonStyle.FILLED)
        NeonButton(text = "Ajouter une ligne", onClick = {})
        NeonButton(text = "Créer cet aliment", onClick = {}, macro = Macro.FIBER)

        // Indisponible mais tapable : c'est le cas de docs/02 pour les modes IA
        // sans clé. L'appui doit se voir, puis expliquer.
        NeonButton(
            text = "Analyser",
            onClick = { explanation = !explanation },
            availability = NeonButtonAvailability.UNAVAILABLE,
        )
        if (explanation) {
            Text(
                text = stringResource(R.string.gallery_button_unavailable_reason),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Inerte : rien à expliquer, rien ne bouge.
        NeonButton(
            text = "Enregistrement…",
            onClick = {},
            availability = NeonButtonAvailability.DISABLED,
        )
    }
}

/** Intertitre d'une section, pour séparer deux familles d'échantillons. */
@Composable
private fun SubHeading(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun BadgeSection() = GallerySection(R.string.gallery_section_badge) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        EntrySource.entries.forEach { source -> SourceBadge(source = source) }
    }
}

@Composable
internal fun TypographySection() = GallerySection(R.string.gallery_section_typography) {
    val ink = MaterialTheme.colorScheme.onSurface
    val inkVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val type = MaterialTheme.typography

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("1 220", style = type.displayLarge, color = ink)
        Text("Titre d'écran", style = type.headlineMedium, color = ink)
        Text("Petit-déjeuner", style = type.titleMedium, color = ink)
        // Deux totaux de largeur identique : c'est ce que doivent donner les
        // chiffres tabulaires, et ce qui saute aux yeux quand ils manquent.
        Text("1 199 kcal", style = type.bodyLarge, color = ink)
        Text("1 200 kcal", style = type.bodyLarge, color = ink)
        Text("Étiquette de jauge", style = type.labelLarge, color = inkVariant)
        Text("Source, date, mention", style = type.labelSmall, color = inkVariant)
    }
}

private val Macro.labelRes: Int
    @StringRes get() =
        when (this) {
            Macro.CALORIES -> R.string.macro_calories
            Macro.PROTEIN -> R.string.macro_protein
            Macro.CARBS -> R.string.macro_carbs
            Macro.SUGARS -> R.string.macro_sugars
            Macro.FAT -> R.string.macro_fat
            Macro.FIBER -> R.string.macro_fiber
        }

private val SwatchSize: Dp = 28.dp
private val SwatchBorder: Dp = 1.dp
private val SmallRingStroke: Dp = 4.dp

private const val RING_SAMPLE_PROGRESS = 0.61f
private const val RING_OVERSHOOT_PROGRESS = 1.18f
