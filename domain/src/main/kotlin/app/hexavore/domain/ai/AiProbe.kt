package app.hexavore.domain.ai

/**
 * Un appel réel, minimal, **avant tout usage**.
 *
 * C'est le bouton « Tester » de [docs/05][ia]. Sa raison d'être est une question de
 * moment : découvrir devant une assiette que la clé a été mal recopiée, ou que le
 * modèle saisi n'existe pas, est le pire instant pour l'apprendre. La vérification se
 * paie une fois, au moment où l'on colle la clé, là où la corriger ne coûte rien.
 *
 * **Il prend la configuration en paramètre plutôt que de lire les réglages** : ce
 * qu'on éprouve est ce qui est dans le formulaire, pas ce qui est déjà enregistré.
 * Tester après avoir écrit reviendrait à enregistrer une clé fausse pour découvrir
 * qu'elle est fausse.
 *
 * [ia]: docs/05-ia.md
 */
fun interface AiProbe {
    suspend fun probe(configuration: AiConfiguration): ProbeOutcome
}

/** Ce qu'un essai apprend. */
sealed interface ProbeOutcome {
    /**
     * Le fournisseur a répondu avec cette clé et ce modèle.
     *
     * @param vision si le mode photo est utilisable. Pour les fournisseurs dont toute
     *   la gamme lit les images, la réponse vient de [AiProvider.vision] et l'essai
     *   n'a rien à découvrir. Pour ceux dont cela dépend du modèle, c'est l'essai qui
     *   devra trancher — en joignant une image minuscule —, et ce sera le travail de
     *   la livraison qui les apporte.
     */
    data class Reachable(val vision: Boolean) : ProbeOutcome

    data class Failed(val error: AiError) : ProbeOutcome
}
