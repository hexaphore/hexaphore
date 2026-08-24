package app.hexavore.domain.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * L'estimation de coût, et ce qu'elle refuse de faire.
 *
 * Les **prix** ne s'éprouvent pas ici : ils viennent des pages de tarifs des
 * fournisseurs, et un test qui les recopierait ne vérifierait que ma capacité à copier
 * deux fois la même chose. Ce qui s'éprouve est la règle de trois, et surtout les deux
 * refus : pas de montant pour un modèle inconnu, pas de zéro à la place d'un inconnu.
 */
class AiPricingTest {
    @Test
    fun `le coût suit les jetons consommés`() {
        // 1 000 000 de jetons d'entree a 5 $ le million, et autant en sortie a 25 $.
        val entry = usage(model = "claude-opus-5", input = 1_000_000, output = 1_000_000)

        assertEquals(30.0, entry.estimatedCost())
    }

    @Test
    fun `un compte minuscule rend un montant minuscule`() {
        // Le cas courant : une analyse coute des fractions de centime, et le compteur
        // doit pouvoir le dire sans arrondir a zero en chemin.
        val entry = usage(model = "gemini-3.5-flash-lite", input = 1_000, output = 500)

        assertEquals(0.30 + 1.25, entry.estimatedCost()!! * 1_000, 1e-9)
    }

    @Test
    fun `un modele inconnu n a pas de prix, et surtout pas zero`() {
        // Un modele saisi a la main, ou n'importe lequel derriere le fournisseur
        // « compatible ». Afficher 0,00 $ en face de deux mille jetons serait un
        // mensonge tranquille.
        val entry = usage(model = "un-modele-de-relais", input = 1_000, output = 1_000)

        assertNull(entry.estimatedCost())
        assertNull(AiPricing.priceOf("un-modele-de-relais"))
    }

    @Test
    fun `les modeles proposes ont tous un tarif, sauf ceux qu on ne peut pas connaitre`() {
        // Ce qui se verifie : la table et l'enumeration ne se desynchronisent pas. Un
        // modele suggere sans tarif afficherait des jetons nus a quelqu'un qui n'a
        // rien saisi lui-meme.
        AiProvider.entries.forEach { provider ->
            provider.suggestedModels.forEach { model ->
                assertNotNull(AiPricing.priceOf(model), "aucun tarif pour $model (${provider.displayName})")
            }
        }
    }

    @Test
    fun `un compte sans jetons ne coûte rien de connu`() {
        // Le fournisseur n'a pas dit ce qu'il comptait : le montant est zero parce que
        // les jetons connus valent zero, et c'est honnete -- l'appel figure quand meme
        // dans le compte des appels.
        val entry = usage(model = "claude-opus-5", input = 0, output = 0)

        assertEquals(0.0, entry.estimatedCost())
    }

    private fun usage(model: String, input: Int, output: Int) = AiUsageEntry(
        provider = AiProvider.ANTHROPIC,
        model = model,
        calls = 1,
        input = input,
        output = output,
    )
}
