package app.hexavore.tooling.ciqual

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * Lecture en flux des fichiers de l'ANSES.
 *
 * `compo.xml` pèse 69 Mo pour 257 816 enregistrements. Le charger en arbre
 * demanderait plus d'un gigaoctet de tas pour produire une base de 4 Mo ; un
 * lecteur en flux le traverse à mémoire constante.
 *
 * Les quatre fichiers ont la même forme — une racine `TABLE`, des enfants tous
 * identiques, et sous chacun des feuilles textuelles — donc un seul lecteur les
 * couvre tous.
 */
internal object XmlRecords {
    private val FACTORY: XMLInputFactory =
        XMLInputFactory.newInstance().apply {
            // Aucune entite externe : le fichier vient du reseau, et un import qui
            // resout des entites est un import qui lit des fichiers arbitraires.
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }

    /**
     * Chaque enregistrement `element`, sous forme de ses feuilles.
     *
     * Une feuille auto-fermante — `<alim_nom_sci missing=" " />`, l'écriture de
     * l'ANSES pour une donnée absente — rend une chaîne vide. C'est voulu :
     * [CiqualValueParser] la lit comme un inconnu, au même titre que `-`, sans que
     * l'appelant ait à distinguer deux façons de ne rien dire.
     */
    fun forEach(input: InputStream, element: String, onRecord: (Map<String, String>) -> Unit) {
        val reader = FACTORY.createXMLStreamReader(input)
        try {
            reader.walk(element, onRecord)
        } finally {
            reader.close()
        }
    }

    private fun XMLStreamReader.walk(element: String, onRecord: (Map<String, String>) -> Unit) {
        var current: MutableMap<String, String>? = null
        var field: String? = null
        val text = StringBuilder()

        while (hasNext()) {
            when (next()) {
                XMLStreamConstants.START_ELEMENT ->
                    if (localName == element) {
                        current = mutableMapOf()
                    } else if (current != null) {
                        field = localName
                        text.setLength(0)
                    }

                XMLStreamConstants.CHARACTERS -> if (field != null) text.append(this.text)

                XMLStreamConstants.END_ELEMENT ->
                    if (localName == element) {
                        current?.let(onRecord)
                        current = null
                    } else if (field != null) {
                        current?.put(field, text.toString().trim())
                        field = null
                    }
            }
        }
    }
}
