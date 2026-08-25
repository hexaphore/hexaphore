package app.hexavore.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.component.NoticeDot
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.notice.Notice

/**
 * Le hub de réglages, **né avec sa deuxième section**.
 *
 * [D59][decisions] l'avait remis à plus tard pour une raison précise : un écran de
 * transit qui ne désigne qu'une seule destination est un écran de trop, et quatre
 * entrées qui n'ouvrent rien ne sont pas une avance. La deuxième section arrive, donc
 * lui aussi — c'est exactement l'échéance qui avait été écrite.
 *
 * **Sauvegarde arrive avec son écran**, et pas avant : c'est la même règle qui l'avait
 * tenue dehors. Les deux dernières de [docs/02][parcours] — Apparence, À propos — n'y
 * figurent toujours pas, et pour la même raison : elles n'ouvriraient rien.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
/**
 * Le hub, branché sur les pastilles.
 *
 * Il emprunte le modèle de l'écran des notifications plutôt que d'en avoir un à lui :
 * c'est le même flux, lu au lieu d'être écrit, et un second modèle aurait fait deux
 * abonnements pour une seule question.
 */
@Composable
internal fun SettingsHubRoute(
    onOpenProfile: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenContribution: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenNotices: () -> Unit,
    onClose: () -> Unit,
    viewModel: NoticeSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsHubScreen(
        onOpenProfile = onOpenProfile,
        onOpenAi = onOpenAi,
        onOpenContribution = onOpenContribution,
        onOpenBackup = onOpenBackup,
        onOpenNotices = onOpenNotices,
        onClose = onClose,
        aiFlagged = state.active.any { it == Notice.AI_NOT_CONFIGURED || it == Notice.AI_KEY_REJECTED },
    )
}

@Composable
internal fun SettingsHubScreen(
    onOpenProfile: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenContribution: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenNotices: () -> Unit,
    onClose: () -> Unit,
    /** La section d'IA porte une pastille : aucune clé ne sert, ou la dernière a été refusée. */
    aiFlagged: Boolean = false,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.settings_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            SectionCard(
                titleRes = R.string.settings_profile_title,
                subtitleRes = R.string.settings_profile_subtitle,
                onClick = onOpenProfile,
            )
            SectionCard(
                titleRes = R.string.settings_ai_title,
                subtitleRes = R.string.settings_ai_subtitle,
                onClick = onOpenAi,
                flagged = aiFlagged,
            )
            SectionCard(
                titleRes = R.string.settings_contribution_title,
                subtitleRes = R.string.settings_contribution_subtitle,
                onClick = onOpenContribution,
            )
            SectionCard(
                titleRes = R.string.settings_backup_title,
                subtitleRes = R.string.settings_backup_subtitle,
                onClick = onOpenBackup,
            )
            SectionCard(
                titleRes = R.string.settings_notices_title,
                subtitleRes = R.string.settings_notices_subtitle,
                onClick = onOpenNotices,
            )
        }
    }
}

@Composable
private fun SectionCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    onClick: () -> Unit,
    flagged: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                if (flagged) {
                    NoticeDot(
                        label = stringResource(R.string.settings_section_flagged),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
