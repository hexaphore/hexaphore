package app.hexaphore.domain.nutrition

/**
 * D'où viennent les valeurs nutritionnelles d'une ligne de journal.
 *
 * À ne pas confondre avec le *mode de saisie* : une ligne peut venir d'une photo
 * tout en tirant ses chiffres de CIQUAL. Confondre les deux rendrait impossible de
 * répondre à la seule question qui compte ici — quelles lignes de mon journal
 * reposent sur une estimation ?
 *
 * L'interface le montre : une estimation ne doit pas se lire comme une donnée
 * mesurée.
 *
 * @see docs/07-modele-de-donnees.md
 */
enum class NutritionSource {
    /** Table de composition nutritionnelle CIQUAL 2025, ANSES. */
    CIQUAL,

    /** Fiche produit Open Food Facts, mise en cache localement. */
    OPEN_FOOD_FACTS,

    /** Aliment créé par l'utilisateur. */
    CUSTOM,

    /**
     * Estimation produite par un modèle, faute de correspondance dans les bases.
     *
     * Jamais versée au catalogue : c'est une supposition, pas une référence.
     */
    AI_ESTIMATE,

    /** Valeur saisie ou corrigée à la main sur une ligne précise. */
    MANUAL,
}
