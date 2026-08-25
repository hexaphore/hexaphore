package app.hexavore.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Le hub, désormais réel : l'accueil y mène, et les sections mènent aux écrans.
 *
 * C'est ce que [docs/02][parcours] décrivait depuis la conception et que
 * [D59][decisions] avait mis en attente de sa deuxième section.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
@Serializable
data object SettingsDestination

/** Les réglages du profil. Aucun argument. */
@Serializable
data object ProfileDestination

/** Les fournisseurs d'IA, leurs clés et leurs modèles. Aucun argument. */
@Serializable
data object AiSettingsDestination

/** Le compte Open Food Facts, et l'instance visée. Aucun argument. */
@Serializable
data object ContributionSettingsDestination

/** Exporter, restaurer, tout effacer. Aucun argument. */
@Serializable
data object BackupDestination

/** Les quatre pastilles et leurs interrupteurs. Aucun argument. */
@Serializable
data object NoticeSettingsDestination

fun NavController.navigateToSettings() = navigate(SettingsDestination)

/**
 * Déclare les six écrans de réglages dans un graphe.
 *
 * Ensemble parce qu'ils partagent une sortie et une seule : **le retour rend l'écran
 * précédent**. Le module ne sait pas lequel c'est, ce qui lui évite de dépendre de
 * `:feature:home`.
 */
fun NavGraphBuilder.settingsScreens(navController: NavController) {
    composable<SettingsDestination> {
        SettingsHubRoute(
            onOpenProfile = { navController.navigate(ProfileDestination) },
            onOpenAi = { navController.navigate(AiSettingsDestination) },
            onOpenContribution = { navController.navigate(ContributionSettingsDestination) },
            onOpenBackup = { navController.navigate(BackupDestination) },
            onOpenNotices = { navController.navigate(NoticeSettingsDestination) },
            onClose = { navController.popBackStack() },
        )
    }
    composable<ProfileDestination> {
        ProfileRoute(onClose = { navController.popBackStack() })
    }
    composable<AiSettingsDestination> {
        AiSettingsRoute(onClose = { navController.popBackStack() })
    }
    composable<ContributionSettingsDestination> {
        ContributionSettingsRoute(onClose = { navController.popBackStack() })
    }
    composable<BackupDestination> {
        BackupRoute(onClose = { navController.popBackStack() })
    }
    composable<NoticeSettingsDestination> {
        NoticeSettingsRoute(onClose = { navController.popBackStack() })
    }
}
