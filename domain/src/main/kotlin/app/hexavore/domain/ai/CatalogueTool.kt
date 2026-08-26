package app.hexavore.domain.ai

import app.hexavore.domain.food.Food
import app.hexavore.domain.nutrition.NutrientValues

/**
 * Le catalogue, tel qu'un modèle peut l'interroger.
 *
 * ### Le défaut que cet outil corrige
 *
 * « Abricot » devenait « Jus d'abricot ». Le modèle disait *abricot*, l'application
 * cherchait « abricot » dans la table de l'ANSES, en tirait vingt candidats, et **son
 * propre score** tranchait — le modèle n'avait jamais voix au chapitre sur la ligne
 * retenue. Pire : la confiance était haute, donc la ligne se remplissait sans être
 * signalée.
 *
 * Ici, c'est le modèle qui choisit. Il connaît l'assiette, il a vu le libellé qu'il a
 * lui-même écrit, et on lui montre ce que la table propose : c'est mieux informé que
 * n'importe quel score de ressemblance de chaînes.
 *
 * ### Ce que la réponse porte, et pourquoi
 *
 * Le nom, le rayon, et **les macros pour 100 g**. Les macros ne sont pas décoratives :
 * elles permettent d'écarter un jus sans deviner. Un jus d'abricot a près de zéro
 * protéine et beaucoup de sucres ; un abricot entier a des fibres. Sans elles, le
 * modèle choisirait sur le seul libellé, c'est-à-dire sur la même information que le
 * score qu'on remplace.
 *
 * Les portions nommées n'y sont pas. Choisir l'unité est un second métier, et le lui
 * confier en même temps qu'identifier l'aliment doublerait les jetons pour une question
 * que la conversion sait déjà trancher.
 *
 * @see docs/04-sources-de-donnees.md
 */
fun interface CatalogueTool {
    /**
     * Ce que le catalogue propose pour chacun des libellés donnés.
     *
     * **L'ordre des groupes suit celui des libellés**, et chaque groupe porte son
     * libellé : le modèle envoie cinq mots d'un coup et doit pouvoir relier chaque
     * liste au sien sans compter les positions.
     *
     * Un libellé sans candidat rend une liste vide — ce n'est pas une erreur, c'est la
     * réponse, et c'est elle qui autorise le modèle à inventer des macros.
     */
    suspend fun candidatesFor(labels: List<String>): List<LabelCandidates>
}

/** Ce que le catalogue propose pour un libellé. */
data class LabelCandidates(val label: String, val candidates: List<FoodCandidate>)

/**
 * Une fiche proposée au modèle, et **la fiche elle-même**.
 *
 * [reference] est ce que le modèle renvoie pour désigner son choix : le code CIQUAL
 * d'une fiche de l'ANSES, ou le code-barres d'un produit. C'est la seule partie qui
 * voyage jusqu'au modèle avec le nom, le rayon et les six teneurs.
 *
 * **[food] ne voyage pas, et c'est ce qui évite un port de relecture.** Le modèle ne
 * peut choisir que parmi ce qu'on lui a montré : la fiche complète est donc déjà là
 * quand il rend sa réponse, avec ses portions et son identifiant. Aller la rechercher
 * par sa référence demanderait une lecture de plus — et une fiche de l'ANSES pas encore
 * copiée dans le catalogue local ne s'y trouverait pas.
 *
 * Une référence que le modèle **invente** ne correspond donc à aucun candidat, et la
 * ligne repart sur le chemin de l'estimation. C'est le bon comportement : on ne remplit
 * pas une ligne avec une fiche qu'on n'a pas.
 */
data class FoodCandidate(val reference: String, val food: Food) {
    val name: String get() = food.name

    /** Le rayon, quand la fiche en a un : « Fruits », « Viandes ». Il désambiguïse à lui seul, souvent. */
    val group: String? get() = food.category?.name

    val per100g: NutrientValues get() = food.per100g
}

/**
 * Le nombre de candidats proposés par libellé.
 *
 * **Six et non vingt.** La résolution interne en demande vingt parce qu'elle les
 * classe elle-même et veut que sa troncature ne décide de rien ; ici, chaque candidat
 * coûte un nom, un rayon et six nombres dans une réponse d'outil, multipliés par le
 * nombre de libellés d'une assiette. Au-delà de six, on paie des lignes que le modèle
 * ne lira pas — les bonnes réponses ne sont jamais septièmes d'un index plein texte.
 */
const val TOOL_CANDIDATES = 6

/**
 * Le nombre d'allers-retours autorisés avec le modèle.
 *
 * **Trois, et c'est un compromis assumé.** Un seul aller-retour ne laisse pas au modèle
 * la possibilité de se rattraper quand aucun candidat ne convient — « merguez grillée »
 * ne trouve rien, et il inventerait des macros là où « saucisse de bœuf » existait.
 * Au-delà de trois, une analyse dépasse la minute et coûte quatre fois le prix d'une
 * analyse simple, pour un gain que rien ne laisse espérer.
 */
const val TOOL_ROUNDS = 3
