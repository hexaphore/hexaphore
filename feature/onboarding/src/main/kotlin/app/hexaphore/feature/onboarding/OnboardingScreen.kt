package app.hexaphore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexaphore.core.designsystem.component.NeonButton
import app.hexaphore.core.designsystem.component.NeonButtonStyle
import app.hexaphore.core.designsystem.theme.Spacing
import app.hexaphore.domain.goal.DailyGoal
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.usecase.GoalPlan
import kotlin.math.roundToInt

/** Les cinq étapes, branchées sur le graphe d'injection. */
@Composable
internal fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        state = state,
        actions = remember(viewModel, onDone) {
            OnboardingActions(
                onAnswers = viewModel::onAnswers,
                onUseReachableDate = viewModel::onUseReachableDate,
                onNext = viewModel::onNext,
                onBack = viewModel::onBack,
                onFinish = { viewModel.onFinish(onDone) },
            )
        },
    )
}

/**
 * Une question par écran, barre de progression en haut.
 *
 * **« Passer » est disponible partout sauf à la première étape.** Un utilisateur
 * pressé doit pouvoir arriver à l'accueil ; les champs sautés prennent une valeur par
 * défaut au moment du calcul, jamais dans le formulaire ([docs/02][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
@Composable
internal fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            LinearProgressIndicator(
                progress = { (state.step.index + 1f) / OnboardingStep.COUNT },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep(state.answers, actions.onAnswers)
                    OnboardingStep.ABOUT_YOU -> AboutYouStep(state.answers, actions.onAnswers)
                    OnboardingStep.ACTIVITY -> ActivityStep(state.answers, actions.onAnswers)
                    OnboardingStep.OBJECTIVE -> ObjectiveStep(state.answers, actions.onAnswers)
                    OnboardingStep.RESULT -> ResultStep(state.plan, actions)
                }
                if (state.failed) Body(stringResource(R.string.onboarding_save_failed))
            }

            Navigation(state, actions)
        }
    }
}

@Composable
private fun Navigation(state: OnboardingUiState, actions: OnboardingActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.step == OnboardingStep.RESULT) {
            NeonButton(
                text = stringResource(R.string.onboarding_start),
                onClick = actions.onFinish,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            NeonButton(
                text = stringResource(R.string.onboarding_next),
                onClick = actions.onNext,
                modifier = Modifier.fillMaxWidth(),
                // Un bouton indisponible reagit quand meme (D28) : il ne se contente
                // pas d'etre gris, il repond a l'appui.
                style = if (state.canContinue) NeonButtonStyle.FILLED else NeonButtonStyle.OUTLINED,
            )
        }
        if (state.step != OnboardingStep.WELCOME) {
            NeonButton(
                text = stringResource(R.string.onboarding_back),
                onClick = actions.onBack,
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.OUTLINED,
            )
        }
    }
}

/**
 * **5. Vos objectifs.** Les six chiffres, chacun avec une phrase d'explication.
 *
 * Quand un garde-fou a mordu, l'écran ne bloque pas : il affiche la date atteignable
 * et propose de s'y caler. L'interdiction pure pousse les gens à mentir sur leur poids
 * pour contourner l'outil ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
@Composable
private fun ResultStep(plan: GoalPlan?, actions: OnboardingActions) {
    if (plan == null) {
        Body(stringResource(R.string.onboarding_result_unavailable))
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepTitle(stringResource(R.string.onboarding_result_title))

        plan.reachableOn?.let { date ->
            Body(stringResource(R.string.onboarding_capped, date.toString()))
            NeonButton(
                text = stringResource(R.string.onboarding_use_reachable_date),
                onClick = actions.onUseReachableDate,
                style = NeonButtonStyle.OUTLINED,
            )
        }
        if (plan.carbsBelowMinimum) Body(stringResource(R.string.onboarding_carbs_low))

        GoalLines(plan.goal)
    }
}

@Composable
private fun GoalLines(goal: DailyGoal) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Macro.entries.forEach { macro ->
            Text(
                text = stringResource(macro.lineRes, goal[macro].roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Body(stringResource(macro.explanationRes))
        }
    }
}

private val Macro.lineRes: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.onboarding_goal_calories
        Macro.PROTEIN -> R.string.onboarding_goal_protein
        Macro.FIBER -> R.string.onboarding_goal_fiber
        Macro.CARBS -> R.string.onboarding_goal_carbs
        Macro.SUGARS -> R.string.onboarding_goal_sugars
        Macro.FAT -> R.string.onboarding_goal_fat
    }

private val Macro.explanationRes: Int
    get() = when (this) {
        Macro.CALORIES -> R.string.onboarding_why_calories
        Macro.PROTEIN -> R.string.onboarding_why_protein
        Macro.FIBER -> R.string.onboarding_why_fiber
        Macro.CARBS -> R.string.onboarding_why_carbs
        Macro.SUGARS -> R.string.onboarding_why_sugars
        Macro.FAT -> R.string.onboarding_why_fat
    }
