package app.hexavore.integration.ai

/**
 * Les libellés à estimer, tels qu'ils partent au modèle.
 *
 * **Une énumération, une par ligne, et rien autour.** Le modèle doit rendre une entrée
 * par libellé demandé et recopier chacun à l'identique ; une phrase — « peux-tu estimer
 * le tofu fumé au sésame et la sauce maison ? » — invite à reformuler, et une
 * reformulation rend une estimation qu'on ne peut plus rattacher à sa ligne.
 *
 * Écrit une fois ici plutôt que dans les trois adaptateurs : c'est la forme de la
 * demande, pas un détail de protocole, et trois copies auraient divergé le jour où
 * quelqu'un y ajoute une consigne.
 */
internal fun List<String>.asRequest(): String = joinToString(separator = "\n")
