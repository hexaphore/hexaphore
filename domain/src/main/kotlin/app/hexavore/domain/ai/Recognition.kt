package app.hexavore.domain.ai

import app.hexavore.domain.food.Food

/** Ce qu'une analyse a rendu, et ce qu'elle a coûté quand le fournisseur le dit. */
data class Recognition(val items: List<RecognizedItem>, val usage: TokenUsage? = null)

/**
 * Une ligne telle que le modèle l'a vue.
 *
 * Ce n'est pas encore un aliment : [label] est du texte libre, et [quantity] avec
 * [unit] sont une estimation. C'est le résolveur qui en fera une fiche et des macros
 * ([docs/04][sources] § Résolution).
 *
 * [sources]: docs/04-sources-de-donnees.md
 */
data class RecognizedItem(
    /** « jus d'orange ». En français, au singulier, sans marque — le prompt l'exige. */
    val label: String,
    /** Toujours strictement positive : une ligne qui n'en a pas est écartée au parsing. */
    val quantity: Double,
    val unit: EstimatedUnit,
    /**
     * La certitude du modèle sur l'identification **et** sur la quantité, dans `[0, 1]`.
     *
     * Elle est affichée ligne par ligne : l'écran de validation est obligatoire, et
     * l'IA fait gagner du temps sans faire autorité.
     */
    val confidence: Float,
    /**
     * Ce que **pèse la ligne entière**, selon le modèle — et `null` s'il s'est tu.
     *
     * C'est ce qui rattrape « 5 cacahuètes ». [quantity] et [unit] ne suffisent pas :
     * « une pièce » n'implique aucune taille, et notre forfait de cent grammes se
     * trompait d'un facteur cent-vingt-cinq sur une cacahuète. Le modèle, lui, a vu
     * l'assiette.
     *
     * **Un poids et non un poids par pièce** : c'est ce qu'on peut lui demander sans
     * qu'il ait à diviser, et c'est directement ce dont la conversion a besoin.
     */
    val grams: Double? = null,
    /**
     * La fiche que le modèle a **choisie lui-même**, quand il en a choisi une.
     *
     * Renseignée par le seul chemin outillé : là, le modèle voit ce que le catalogue
     * propose et tranche, au lieu de laisser un score de ressemblance de chaînes le
     * faire à sa place. C'est ce qui corrige « Abricot » devenu « Jus d'abricot ».
     *
     * `null` sur le chemin ordinaire, et `null` aussi quand le modèle n'a rien trouvé
     * qui convienne — auquel cas la ligne repart vers l'estimation, comme avant.
     *
     * **Une fiche et non une référence** : le modèle ne peut choisir que parmi ce
     * qu'on lui a montré, donc la fiche est déjà là quand sa réponse arrive. Aller la
     * relire par sa référence coûterait une lecture, et une fiche de l'ANSES pas
     * encore copiée dans le catalogue local ne s'y trouverait pas.
     */
    val chosen: Food? = null,
)

/**
 * L'unité dans laquelle le modèle exprime une quantité.
 *
 * **Ce n'est pas [QuantityUnit][app.hexavore.domain.diary.QuantityUnit]**, et la
 * confusion coûterait cher. Celle du journal porte un poids en grammes, parce qu'une
 * ligne doit rester relisible sans sa fiche ([D42][decisions]). Celle-ci n'en porte
 * aucun : « un bol » ne pèse rien tant que le résolveur n'a pas décidé ce que pèse un
 * bol. C'est un **vocabulaire d'estimation**, et le convertir est précisément le
 * travail de l'étape suivante ([docs/04][sources] § Conversion des quantités).
 *
 * [docs/05][ia] appelait ce type `QuantityUnit`, avant que le journal n'en ait un
 * autre sous ce nom. Le doublon est levé en [D72][decisions].
 *
 * [ia]: docs/05-ia.md
 * [sources]: docs/04-sources-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
enum class EstimatedUnit {
    G,
    ML,
    PIECE,
    SLICE,
    TBSP,
    TSP,
    BOWL,
    PLATE,
    GLASS,
}

/**
 * Les jetons consommés, quand l'API les renvoie.
 *
 * Facultatif parce que tous les fournisseurs ne les donnent pas, et le compteur de
 * coût préfère ne rien annoncer qu'annoncer un zéro qui passerait pour gratuit.
 */
data class TokenUsage(val input: Int, val output: Int)
