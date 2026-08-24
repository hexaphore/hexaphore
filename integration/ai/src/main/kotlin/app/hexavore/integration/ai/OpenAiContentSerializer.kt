package app.hexavore.integration.ai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * `content` est une chaîne **ou** un tableau, et le JSON ne le dit pas autrement que
 * par son type.
 *
 * Un `String?` et une `List?` côte à côte auraient laissé exprimer « les deux à la
 * fois », qui n'existe pas, et « ni l'un ni l'autre », qui rend un `400`. Le type
 * scellé rend les deux inécrivables ; ce sérialiseur est le prix à payer pour ça, et
 * il tient en une ligne par cas.
 *
 * **La chaîne, et non le tableau, dès qu'il n'y a pas d'image.** Les deux formes sont
 * légales chez OpenAI, mais un relais compatible n'accepte parfois que la première —
 * et c'est précisément ce fournisseur-là qu'on ne peut pas tester.
 */
internal object OpenAiContentSerializer : KSerializer<OpenAiContent> {
    private val parts = ListSerializer(OpenAiPart.serializer())

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OpenAiContent) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(
            when (value) {
                is OpenAiContent.Text -> JsonPrimitive(value.value)
                is OpenAiContent.Parts -> json.json.encodeToJsonElement(parts, value.value)
            },
        )
    }

    /**
     * Jamais appelé : ce type ne sert qu'aux requêtes.
     *
     * La réponse porte son contenu dans un champ à part, `OpenAiResponseMessage.content`,
     * qui est une chaîne et le reste. Écrire ici une lecture que rien n'exerce
     * reviendrait à livrer du code non éprouvé dans le chemin le plus fragile du module.
     */
    override fun deserialize(decoder: Decoder): OpenAiContent =
        error("Le contenu d un message ne se relit pas : il ne voyage que dans les requetes.")
}
