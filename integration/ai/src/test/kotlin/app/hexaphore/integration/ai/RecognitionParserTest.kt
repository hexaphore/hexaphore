package app.hexaphore.integration.ai

import app.hexaphore.domain.ai.AiError
import app.hexaphore.domain.ai.EstimatedUnit
import app.hexaphore.domain.ai.Recognition
import app.hexaphore.domain.ai.RecognitionOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Les cas que `docs/10` déclare obligatoires : JSON entouré de texte, tronqué, unité
 * inconnue, confiance hors bornes, tableau vide.
 *
 * C'est la seule règle de ce module qui s'éprouve sans réseau — tout le reste est
 * un appel HTTP, et il a son propre montage.
 */
class RecognitionParserTest {
    @Test
    fun `du texte autour du tableau ne gene pas`() {
        val brut = """
            Voici ce que je vois sur la photo :
            [{"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8}]
            J'espere que cela vous aide.
        """.trimIndent()

        assertEquals(listOf("riz"), brut.lignes().map { it.label })
    }

    @Test
    fun `des accolades avant le tableau ne detournent pas la lecture`() {
        // Le premier bloc equilibre n'est pas forcement du JSON. La regle est « le
        // premier bloc qui se decode », sans quoi cette reponse serait declaree
        // illisible alors qu'elle est juste.
        val brut = """
            Analyse au format {label, quantity} :
            [{"label": "pomme", "quantity": 1, "unit": "PIECE", "confidence": 0.9}]
        """.trimIndent()

        assertEquals(listOf("pomme"), brut.lignes().map { it.label })
    }

    @Test
    fun `les clotures markdown ne genent pas`() {
        val brut = "```json\n[{\"label\": \"pain\", \"quantity\": 80, \"unit\": \"G\", \"confidence\": 1}]\n```"

        assertEquals(listOf("pain"), brut.lignes().map { it.label })
    }

    @Test
    fun `un objet qui enveloppe le tableau se lit aussi`() {
        // Tous les fournisseurs n'acceptent pas un tableau a la racine : certains
        // schemas imposent un objet.
        val brut = """{"items": [{"label": "oeuf", "quantity": 2, "unit": "PIECE", "confidence": 0.7}]}"""

        assertEquals(listOf("oeuf"), brut.lignes().map { it.label })
    }

    @Test
    fun `un crochet non appaire dans un libelle ne referme pas le tableau`() {
        // Un crochet **equilibre** dans un libelle ne prouverait rien : il s'ouvre et
        // se referme, donc le compte revient au meme qu'on regarde les chaines ou
        // non. C'est le crochet solitaire qui coupe le tableau au milieu et fait
        // declarer illisible une reponse qui ne l'est pas.
        val brut = """[{"label": "riz [basmati", "quantity": 150, "unit": "G", "confidence": 0.5}]"""

        assertEquals(listOf("riz [basmati"), brut.lignes().map { it.label })
    }

    @Test
    fun `un tableau tronque est illisible, il ne rend pas la moitie des lignes`() {
        val brut = """[{"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8}, {"label": "poul"""

        assertEquals(RecognitionOutcome.Failed(AiError.Unparseable), parseRecognition(brut))
    }

    @Test
    fun `une reponse sans le moindre bloc est illisible`() {
        assertEquals(
            RecognitionOutcome.Failed(AiError.Unparseable),
            parseRecognition("Je ne peux pas analyser cette image."),
        )
    }

    @Test
    fun `un tableau vide n est pas un succes silencieux`() {
        // La promesse de docs/05 : jamais de liste vide. Sans ce cas, l'ecran de
        // validation s'ouvrirait sans aucune ligne et sans rien dire.
        assertEquals(RecognitionOutcome.Failed(AiError.NothingRecognized), parseRecognition("[]"))
    }

    @Test
    fun `une unite inconnue retombe sur PIECE`() {
        val brut = """[{"label": "soupe", "quantity": 1, "unit": "LOUCHE", "confidence": 0.4}]"""

        assertEquals(EstimatedUnit.PIECE, brut.lignes().single().unit)
    }

    @Test
    fun `une unite se reconnait quelle que soit sa casse`() {
        val brut = """[{"label": "lait", "quantity": 200, "unit": "ml", "confidence": 0.6}]"""

        assertEquals(EstimatedUnit.ML, brut.lignes().single().unit)
    }

    @Test
    fun `une confiance hors bornes est ramenee dans l intervalle`() {
        val brut = """
            [{"label": "riz", "quantity": 150, "unit": "G", "confidence": 87},
             {"label": "huile", "quantity": 5, "unit": "ML", "confidence": -2}]
        """.trimIndent()

        assertEquals(listOf(1f, 0f), brut.lignes().map { it.confidence })
    }

    @Test
    fun `une ligne sans libelle est ecartee`() {
        val brut = """
            [{"label": "  ", "quantity": 150, "unit": "G", "confidence": 0.8},
             {"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8}]
        """.trimIndent()

        assertEquals(listOf("riz"), brut.lignes().map { it.label })
    }

    @Test
    fun `une quantite nulle ou negative ecarte la ligne`() {
        val brut = """
            [{"label": "sel", "quantity": 0, "unit": "G", "confidence": 0.8},
             {"label": "poivre", "quantity": -3, "unit": "G", "confidence": 0.8},
             {"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8}]
        """.trimIndent()

        assertEquals(listOf("riz"), brut.lignes().map { it.label })
    }

    @Test
    fun `une ligne illisible n emporte pas les autres`() {
        // Une assiette de six aliments dont un mal forme vaut mieux qu'un echec
        // complet : c'est l'utilisateur qui corrigera la ligne manquante.
        val brut = """
            [{"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8},
             "poulet",
             {"label": "haricots", "quantity": 80, "unit": "G", "confidence": 0.6}]
        """.trimIndent()

        assertEquals(listOf("riz", "haricots"), brut.lignes().map { it.label })
    }

    @Test
    fun `un champ inconnu du modele ne fait pas echouer la ligne`() {
        val brut = """[{"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8, "calories": 520}]"""

        assertEquals(listOf("riz"), brut.lignes().map { it.label })
    }

    @Test
    fun `la consommation de jetons voyage avec le resultat quand on la connait`() {
        val brut = """[{"label": "riz", "quantity": 150, "unit": "G", "confidence": 0.8}]"""

        val rendu = parseRecognition(brut, JETONS)

        assertEquals(JETONS, (rendu as RecognitionOutcome.Recognized).recognition.usage)
    }

    /** Les lignes rendues, ou l'échec du test si la réponse n'a pas été reconnue. */
    private fun String.lignes() = recognition().items

    private fun String.recognition(): Recognition {
        val rendu = parseRecognition(this)
        assertEquals(RecognitionOutcome.Recognized::class.java, rendu.javaClass, "reponse non reconnue : $this")
        return (rendu as RecognitionOutcome.Recognized).recognition
    }

    private companion object {
        val JETONS = app.hexaphore.domain.ai.TokenUsage(input = 412, output = 96)
    }
}
