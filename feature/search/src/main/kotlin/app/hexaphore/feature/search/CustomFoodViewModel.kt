package app.hexaphore.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hexaphore.domain.food.Barcode
import app.hexaphore.domain.food.CustomFoodDraft
import app.hexaphore.domain.food.FoodId
import app.hexaphore.domain.nutrition.Macro
import app.hexaphore.domain.nutrition.NutrientValues
import app.hexaphore.domain.usecase.SaveCustomFood
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Le formulaire d'aliment personnel.
 *
 * **Du texte et non des nombres**, pour la même raison que l'écran de validation :
 * un champ qui rendrait un `Double` reformaterait « 12, » en « 12 » à la frappe
 * suivante, et « 12,5 » deviendrait impossible à saisir.
 *
 * Il s'ouvre avec le nom déjà tapé dans la recherche : c'est le parcours « aucun
 * résultat », et retaper « pâtes de mamie » serait la première chose que
 * l'utilisateur reprocherait à cet écran.
 */
@HiltViewModel
internal class CustomFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveCustomFood: SaveCustomFood,
) : ViewModel() {
    /**
     * Le code-barres est relu **une fois** et ne devient jamais un champ : il n'a pas
     * été tapé, il a été lu, et le laisser modifier ferait porter à la fiche un code
     * qui n'est pas celui de l'emballage qu'on a devant soi.
     */
    private val barcode: Barcode? =
        savedStateHandle.get<String>(CustomFoodDestination.BARCODE)?.let(Barcode::of)

    private val form = MutableStateFlow(
        CustomFoodForm(
            name = savedStateHandle.get<String>(CustomFoodDestination.NAME).orEmpty(),
            barcode = barcode,
        ),
    )
    private val status = MutableStateFlow(Status.EDITING)

    val uiState: StateFlow<CustomFoodUiState> =
        combine(form, status) { form, status ->
            when (status) {
                Status.EDITING -> CustomFoodUiState.Editing(form, saving = false)
                Status.SAVING -> CustomFoodUiState.Editing(form, saving = true)
                Status.FAILED -> CustomFoodUiState.Error(form)
                Status.SAVED -> CustomFoodUiState.Saved(checkNotNull(saved))
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = CustomFoodUiState.Editing(form.value, saving = false),
        )

    private var saved: FoodId? = null

    fun onNameChange(value: String) = form.update { it.copy(name = value) }

    fun onBrandChange(value: String) = form.update { it.copy(brand = value) }

    fun onServingChange(value: String) = form.update { it.copy(serving = value) }

    fun onMacroChange(macro: Macro, value: String) = form.update { it.copy(macros = it.macros + (macro to value)) }

    /** Après un échec d'écriture : la saisie n'a pas bougé, il n'y a qu'à réessayer. */
    fun onRetry() {
        status.value = Status.EDITING
    }

    fun onSave() {
        val draft = form.value.toDraft()
        if (!draft.complete || status.value == Status.SAVING) return

        status.value = Status.SAVING
        viewModelScope.launch {
            val written = runCatching { saveCustomFood(draft) }
            saved = written.getOrNull()
            status.value = if (written.isSuccess) Status.SAVED else Status.FAILED
        }
    }

    private enum class Status { EDITING, SAVING, SAVED, FAILED }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Ce que le formulaire porte, en texte. */
internal data class CustomFoodForm(
    val name: String = "",
    val brand: String = "",
    val serving: String = "",
    val macros: Map<Macro, String> = emptyMap(),
    /** Lu, jamais tapé : il s'affiche et ne s'édite pas. */
    val barcode: Barcode? = null,
) {
    fun toDraft(): CustomFoodDraft = CustomFoodDraft(
        name = name,
        brand = brand,
        barcode = barcode,
        // Un champ vide vaut inconnu, jamais zero. C'est la regle du projet,
        // appliquee la ou elle est le plus facile a trahir : il suffirait de lire
        // un champ vide comme « 0 » pour eviter tout traitement du cas nul.
        per100g = Macro.entries.fold(NutrientValues()) { values, macro ->
            values.with(macro, number(macros[macro].orEmpty()))
        },
        defaultServingG = number(serving),
    )

    val complete: Boolean get() = toDraft().complete
}

/** Où en est le formulaire. */
internal sealed interface CustomFoodUiState {
    data class Editing(val form: CustomFoodForm, val saving: Boolean) : CustomFoodUiState

    data class Error(val form: CustomFoodForm) : CustomFoodUiState

    /** Enregistré : l'écran se referme sur la fiche, qui devient une ligne de saisie. */
    data class Saved(val id: FoodId) : CustomFoodUiState
}

/**
 * Un nombre tel qu'un clavier français le produit.
 *
 * La virgule est acceptée au même titre que le point. Une chaîne vide rend `null` :
 * le champ n'a pas été renseigné, ce qui n'est pas zéro.
 */
private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
