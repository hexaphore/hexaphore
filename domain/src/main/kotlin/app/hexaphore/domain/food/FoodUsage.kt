package app.hexaphore.domain.food

import java.time.Instant

/**
 * Verser au catalogue les fiches d'un plat, et noter qu'elles ont servi.
 *
 * **Deux gestes en un seul appel, et c'est voulu.** Un aliment de la table de
 * l'ANSES n'est pas dans le catalogue tant qu'il n'a pas été mangé : copier les
 * 3 484 lignes à l'installation gonflerait la base, les sauvegardes et la recherche
 * avec 99 % de contenu jamais utilisé ([docs/07][modele]). Sa fiche entre donc au
 * moment exact où une entrée de journal se met à la citer — et les séparer en deux
 * appels laisserait, entre les deux, un instant où l'entrée désigne une fiche
 * absente.
 *
 * Prend les fiches entières et non leurs identifiants : c'est ce qui permet de les
 * écrire sans aller les relire ailleurs, et ce qui rend visible, à la lecture de
 * `LogDish`, que l'enregistrement d'un plat écrit aussi dans le catalogue.
 *
 * **Le moment compte.** C'est l'enregistrement du plat qui marque l'usage, pas la
 * sélection dans la liste de résultats : « Récents » dit ce qu'on mange, pas ce
 * qu'on a consulté puis abandonné.
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
interface FoodUsage {
    /**
     * Écrit [foods] au catalogue si elles n'y sont pas, et note leur usage à [at].
     *
     * Une collection vide est légitime : c'est le cas d'un plat entièrement tapé à
     * la main, et celui d'un plat rouvert, dont les fiches sont déjà au catalogue.
     *
     * Les valeurs d'une fiche déjà connue **ne sont pas réécrites**. Une correction
     * apportée à un aliment personnel ne doit pas être défaite par un plat rouvert
     * qui porte encore l'ancienne version.
     *
     * @return **ce que chaque fiche est devenue**, de l'identifiant qu'elle portait
     *   vers celui sous lequel elle est rangée. Les deux diffèrent dès qu'une fiche de
     *   l'ANSES était déjà au catalogue : le résultat de recherche porte un
     *   identifiant provisoire, la fiche rangée garde le sien, et une entrée de journal
     *   qui citerait le provisoire désignerait une fiche absente — ce que la base
     *   refuse. Rendre la correspondance ici est la seule façon de la connaître :
     *   c'est le geste d'écriture qui la découvre.
     */
    suspend fun remember(foods: Collection<Food>, at: Instant): Map<FoodId, FoodId>
}
