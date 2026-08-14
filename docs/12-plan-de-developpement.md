# 12 — Plan de développement

Dans quel ordre construire, et comment savoir qu'une étape est finie.

## Principe : des tranches verticales

Le découpage naturel — « d'abord toute l'interface, ensuite on branche » — découpe le travail en couches horizontales. Une couche n'est jamais terminée, donc jamais confrontée à la réalité : on dessine des écrans contre des formes de données imaginées, et le jour où le domaine arrive avec ses vraies formes, il faut refaire les écrans.

Chaque itération traverse donc **toutes** les couches et livre une capacité utilisateur complète, installable le soir même sur un vrai téléphone.

Une exception assumée : l'itération 0, qui est horizontale parce que son contenu ne peut pas être ajouté après coup.

### La technique qui réconcilie les deux

À l'intérieur d'une tranche, commencer par l'écran est une bonne idée — à condition de l'écrire **contre les interfaces du domaine, avec une implémentation en mémoire**. L'écran est réel, les contrats sont réels, et remplacer le faux par Room est un changement d'une ligne dans le module Hilt.

C'est le rôle de `:core:testing` : les implémentations bidon n'y sont pas des béquilles de test, ce sont les premières implémentations des ports.

## Définition de « terminé »

Elle s'applique à chaque tranche, sans exception. Une tranche qui coche cinq critères sur six n'est pas terminée à 83 % — elle n'est pas terminée.

- La capacité fonctionne sur un appareil réel, pas seulement dans un aperçu.
- `./gradlew check` passe : tests, ktlint, detekt, Android Lint.
- Les règles du domaine touchées par la tranche sont testées, y compris leurs cas limites.
- Aucune couleur, durée ou dimension codée en dur hors de `:core:designsystem`.
- Les tranches précédentes fonctionnent toujours.
- Toute décision prise en route est inscrite dans [11](11-decisions.md), même en trois lignes.

---

## Itération 0 — Le socle

La seule itération qui ne livre aucune fonctionnalité. Son contenu est précisément ce qu'on ne peut pas rétro-installer.

**Contenu**

- Projet Gradle, catalogue de versions dans `gradle/libs.versions.toml`, aucune version écrite en dur dans un `build.gradle.kts`.
- **Trois modules seulement** : `:app`, `:domain`, `:core:designsystem`. Les treize autres du doc [06](06-architecture.md) naissent quand ils ont un contenu — seize modules vides, c'est de l'architecture en vitrine.
- `:domain` déclaré en module **Kotlin/JVM**, sans le plugin Android.
- Hilt câblé de bout en bout, avec un seul point d'injection pour prouver que ça marche.
- ktlint, detekt et les trois règles personnalisées du doc [10](10-qualite-et-livraison.md#analyse-statique), plus le workflow GitHub Actions.
- Design system complet : `MacroColors`, `NeonTheme`, `Motion`, puis `MacroRing`, `MacroBar`, `NeonButton`, `SourceBadge`. `MacroHexagon` s'y ajoute en tranche 1, quand il a des chiffres réels à montrer.

**Terminé quand**

- Un `import android.*` ajouté dans `:domain` **fait échouer le build**, et le message dit pourquoi.
- Une couleur écrite en dur dans `:app` fait échouer detekt.
- L'application affiche une galerie des composants sur un appareil, en thème sombre et clair.
- La CI est verte sur une pull request, en moins de 8 minutes.

**Piège** : céder à la tentation de créer les seize modules « pour être tranquille ». Un module se crée le jour où on a un fichier à y mettre.

---

## Tranche 1 — « Je vois ma journée »

**Contenu**

- `:core:database` : Room avec les tables `dish` et `food_entry`. Migrations actives dès la version 1, schémas exportés et versionnés. La table `food` attend la tranche 3, qui est la première à la remplir ([D34](11-decisions.md)).
- `:domain` : les modèles `Macros`, `MacroTotals`, `FoodEntry`, `Dish`, `DaySummary` ; le port `DiaryRepository` ; le cas d'usage `GetDaySummary` ; les abstractions `Clock` et `DispatcherProvider`.
- `:feature:home` : l'écran d'accueil réel, avec l'hexagone des macros ([D33](11-decisions.md)) et les barres chiffrées.
- Deux implémentations du port : `InMemoryDiaryRepository` d'abord, `RoomDiaryRepository` ensuite.
- Un objectif **codé en dur** (2 000 kcal et sa répartition), remplacé en tranche 4. C'est une dette assumée, et elle est écrite ici.

**Terminé quand**

- L'accueil affiche les six compteurs alimentés par Room, et chaque plat expose ses six apports — pas seulement ses calories.
- Basculer entre l'implémentation en mémoire et Room ne change **qu'une ligne** dans le module Hilt.
- `GetDaySummary` est testé avec une horloge figée.
- Un test de migration existe, même trivial, pour que le mécanisme soit en place.

**Pièges** : un `LocalDate.now()` écrit en dur quelque part ; `fallbackToDestructiveMigration` « le temps du développement ».

---

## Tranche 2 — « J'ajoute un aliment à la main »

C'est le vrai premier jalon : à la fin de cette tranche, **l'application est utilisable au quotidien**. Objectif en dur et saisie manuelle, mais tu peux t'en servir — et donc juger l'ergonomie pour de bon.

**Contenu**

- `:feature:entry` : l'**écran de validation** décrit en [02](02-parcours-et-ecrans.md#écran-de-validation-dentrée). Lignes éditables, quantité, macros dépliables, date. Pas de repas de destination : les lignes forment un plat ([D31](11-decisions.md)).
- `EntryDraft` : le modèle d'entrée de cet écran, **indépendant de la source**.
- Cas d'usage `LogDish`, `UpdateDish`, `DeleteEntry`, `RestoreDish`, `GetDishDraft`, `CreateDraft` — le plat est l'unité de saisie, la ligne l'unité de suppression ([D43](11-decisions.md)).
- Navigation Compose à routes typées : l'accueil et la validation deviennent deux destinations.
- Suppression par balayage, avec annulation par `Snackbar`.

~~Le port `CustomFoodStore` et le formulaire de création d'un aliment personnel.~~ Reporté en tranche 3, avec la table `food` qui lui donne un sens et la recherche qui le rend utile ([D40](11-decisions.md)).

**Terminé quand**

- On ajoute, modifie et supprime une ligne, et les totaux suivent immédiatement.
- L'écran de validation ne contient **aucune** référence à un mode de saisie particulier.
- Un test prouve que modifier un aliment ne change pas les entrées déjà enregistrées ([D05](11-decisions.md)). *Sans table `food`, la forme éprouvable de cette règle est celle-ci : rouvrir un plat rend les valeurs **figées à l'enregistrement**, et corriger une ligne n'en réécrit aucune autre. La version complète arrive en tranche 3, quand un aliment existera pour être modifié.*

**Le piège central du projet est ici.** Cet écran est le point de convergence des quatre modes de saisie. Écrit « pour la saisie manuelle », il faudra le généraliser trois fois. Il doit accepter dès maintenant *n* lignes venant de *n'importe quelle* source.

---

## Tranche 3 — « Je cherche un aliment »

**Contenu**

- `:tooling:ciqual-import` : tâche Gradle qui convertit le XML de l'ANSES en `ciqual.db`.
- `CiqualValueParser` et ses tests : `traces`, `< n`, `-`, `NC`, virgule décimale, chaîne vide, **et l'écriture inconnue**, qui arrête l'import.
- ~~Table FTS5 avec `unicode61 remove_diacritics 2`.~~ FTS4 sur un nom normalisé à l'import : ni l'un ni l'autre ne tient sous `minSdk 26` ([D49](11-decisions.md)).
- `servings.csv`, la table des portions usuelles.
- Table `food`, migration 1 → 2, colonne `food_entry.food_id` — les dettes échues de [D40](11-decisions.md).
- Modale de recherche, récents, favoris, formulaire d'aliment personnel. Ports `FoodSearch`, `FoodLookup`, `RecentFoods`, `FavoriteFoods`, `CustomFoodStore`, `FoodUsage`.

**Terminé quand**

- Des résultats apparaissent en moins de 150 ms, hors-ligne, dès le deuxième caractère et 120 ms après la dernière frappe ([D23](11-decisions.md)).
- « creme brulee » trouve « crème brûlée ».
- Chaque convention d'écriture de CIQUAL a son cas de test, et `null` ne se confond jamais avec `0`.
- Un test prouve que modifier un aliment ne change pas les entrées déjà enregistrées ([D05](11-decisions.md)) — la forme complète, que la tranche 2 ne pouvait pas écrire.

**Piège** : traiter une valeur inconnue comme un zéro. C'est l'erreur la plus facile à commettre et la plus difficile à repérer — elle fausse des mois de journal en silence.

> **Livrée.** 3 484 aliments et 67 portions en 824 Ko, `creme brulee` vérifié sur les données livrées. Ce que la tranche ne construit pas est écrit en [D50](11-decisions.md).

---

## Tranche 4 — « L'application connaît mon objectif »

**Contenu**

- `:domain` : `EnergyExpenditureCalculator`, `MacroDistributionPolicy`, `GoalSafetyPolicy`, `CalculateDailyGoal`.
- Tables `profile`, `weight_entry`, et `goal` **versionnée**.
- `:feature:onboarding` : les cinq étapes.
- `:feature:settings` : réglages profil, recalcul, édition manuelle avec verrouillage des champs édités.

**Terminé quand**

- L'exemple chiffré du doc [03](03-nutrition-calculs.md#exemple-complet) passe en test, au kcal près, **contrôle de cohérence énergétique compris** — c'est lui qui a révélé les 70 kcal de fibres comptées deux fois ([D24](11-decisions.md)).
- Chaque garde-fou est testé sur ses deux bornes.
- Une journée passée est comparée à l'objectif **actif ce jour-là**.
- ~~L'objectif codé en dur de la tranche 1 a disparu, et la dette correspondante est rayée.~~ **Fait** : `DailyGoal.Placeholder` n'existe plus, `GetDaySummary` lit l'objectif actif du jour, et `DaySummary.goal` est nullable ([D55](11-decisions.md)).
- On relit et corrige son profil ; un recalcul suit, et un objectif **saisi à la main** y survit et se voit comme tel ([D59](11-decisions.md), [D60](11-decisions.md)).

**Piège** : mettre à jour un objectif en place « parce que c'est plus simple ». Voir [D04](11-decisions.md).

> **Livrée.** Le calcul, les tables versionnées, les cinq étapes et les réglages profil. L'exemple de [03](03-nutrition-calculs.md#exemple-complet) passe au kcal près, et chaque garde-fou est éprouvé sur ses deux bornes. La colonne `manual_fields` a vécu le temps d'une décision : le verrou par compteur est devenu un mode porté par `origin`, et elle est partie en migration 3 → 4 ([D60](11-decisions.md)). Ce que la tranche ne construit pas est écrit en [D55](11-decisions.md) et [D59](11-decisions.md).

---

## Tranche 5 — « Je scanne »

**Contenu**

- `:integration:scanner` : CameraX et ML Kit, formats EAN/UPC uniquement, anti-rebond à deux lectures identiques.
- `:integration:openfoodfacts` : Retrofit, DTO, correspondance, `User-Agent` obligatoire, retrait exponentiel.
- Mise en cache permanente dans `food`, parcours complet du produit absent.

**Terminé quand**

- Un scan affiche la fiche en moins de 2 s ; le deuxième scan du même produit est instantané et fonctionne en mode avion.
- Un produit sans valeur de fibres s'enregistre quand même, avec le trou visible.
- Un produit absent d'Open Food Facts se crée à la main **en conservant son code-barres**.

**Piège** : oublier le `User-Agent`. Open Food Facts bloque les clients anonymes, et le symptôme ressemble à une panne réseau.

> **En cours.** Le client Open Food Facts ([D63](11-decisions.md#d63--le-code-barres-est-une-clé-et-le-client-séprouve-devant-un-vrai-serveur---validée)), puis le cache : `LookupBarcode` lit le catalogue avant le réseau, une fiche récupérée y est versée avec sa date, et `is_liquid` et `fetched_at` arrivent en migration 5 → 6 ([D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée)). Puis `:integration:scanner` : le décodage, l'anti-rebond éprouvé sur la JVM, un aliment personnel qui garde son code-barres, et un cinquième `DraftOrigin` que l'écran de validation a accepté **sans être touché** ([D65](11-decisions.md#d65--le-décodeur-est-un-module-à-part-et-sa-seule-règle-tient-sur-la-jvm---validée)). Puis la modale elle-même, ses quatre états et le graphe qui réunit les modes de saisie ([D66](11-decisions.md#d66--la-modale-de-scan-et-les-trois-modes-de-saisie-réunis-dans-le-graphe---validée)).

Enfin la suggestion « Chercher dans Open Food Facts », dernière dette de [D50](11-decisions.md#d50--ce-que-la-tranche-3-ne-construit-pas---validée) : une recherche **par nom**, offerte en dernière ligne de résultats et déclenchée sur un tap ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)).

> **Ce qui a tourné sur appareil.** Le scan de bout en bout : caméra, permission, décodage, appel réel à Open Food Facts, mise en cache, et création manuelle d'un produit absent reconnu au scan suivant. Deux défauts d'affichage y ont été trouvés et corrigés — une surimpression illisible ([D68](11-decisions.md#d68--un-voile-est-une-surface-et-il-reste-sombre-dans-les-deux-thèmes---validée)) et un aperçu qui continuait de tourner sous elle ([D69](11-decisions.md#d69--laperçu-se-fige-sur-la-trame-qui-a-porté-la-lecture---validée)) ; aucun des deux n'était visible autrement qu'en tenant le téléphone.

> **Ce qui n'a jamais tourné.** Les migrations 3 → 4, 4 → 5 et 5 → 6 **sur une base peuplée** — elles n'ont tourné que sous Robolectric, et ne s'éprouvent qu'en installant **par-dessus**. La suggestion « Chercher dans Open Food Facts » et sa liste distante ([D67](11-decisions.md#d67--la-recherche-par-nom-se-demande-et-la-date-appartient-à-celui-qui-récupère---validée)). Les écrans de [D59](11-decisions.md#d59--le-profil-se-corrige-et-le-verrou-survit-au-recalcul---en-partie-remplacée-par-d60) à [D62](11-decisions.md#d62--un-favori-est-un-modèle-vivant-et-létoile-est-son-seul-interrupteur---validée) : réglages profil, menu contextuel, étoile, liste des favoris. Enfin les deux corrections ci-dessus, qui ne se jugent que sur l'appareil, **en thème sombre et clair**. Cette tranche n'est **pas terminée** au sens de la définition ci-dessus tant qu'un `installDebug` **par-dessus** ne l'a pas confirmé.

---

## Tranche 6 — « Je photographie ou je décris »

**Contenu**

- `:integration:ai` : `FoodRecognizer`, `RecognitionInput` en `Photo | Text`, les six implémentations.
- Prompt versionné dans les assets, parseur tolérant, `NutritionResolver`.
- Clés dans `EncryptedSharedPreferences`, intercepteur de redaction, compteur de coût.
- Première implémentation de `FoodContributionTarget` ([D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée)).

**Terminé quand**

- Une photo produit une proposition éditable en moins de 10 s.
- Les modes photo et texte partagent **le même pipeline** : seule l'entrée diffère.
- Ajouter un septième fournisseur ne demande qu'une classe et une entrée d'énumération.
- Un test prouve que le fichier temporaire est supprimé, y compris quand l'appel échoue.
- Aucune clé n'apparaît dans les journaux, en debug comme en release.
- Une fiche saisie à la main **peut** être reversée à Open Food Facts, et rien ne part sans un geste explicite.

**Piège** : un `when` sur le fournisseur ailleurs que dans la fabrique. C'est le signal que l'abstraction a fui.

**La contribution n'est pas là par affinité de sujet.** Elle est là parce que le `NutritionResolver` apporte `density`, donc la première fiche Open Food Facts qui se consulte, donc le premier écran d'où elle puisse s'offrir — le même que celui qu'attend `user_edited_fields` depuis [D64](11-decisions.md#d64--le-cache-prend-date-et-un-code-barres-ne-traverse-pas-deux-espaces-de-noms---validée). Trois points restent ouverts et sont écrits en [D70](11-decisions.md#d70--contribuer-à-open-food-facts-entre-en-tranche-6-parce-que-la-couverture-nest-pas-la-même-partout---validée) : compte ou anonyme, ce qu'on envoie d'une fiche partielle, et la forme du consentement — c'est la première écriture sortante de l'application.

---

## Tranche 7 — « Je consulte mon historique »

**Contenu**

- Bandeau calendrier, calendrier étendu, écran Journée.
- Journal de poids, moyenne mobile sur 7 jours, `SuggestGoalAdjustment`.

**Terminé quand**

- Une journée sans saisie est visuellement neutre, et n'est **jamais** comptée comme une journée à zéro.
- Aucune suggestion d'ajustement n'est appliquée sans accord explicite.
- Les conditions de déclenchement — adhérence, persistance, nombre de pesées — sont testées.

---

## Tranche 8 — « Mes données sont à l'abri »

**Contenu**

- Port `BackupTarget`, instantané JSON versionné avec sa chaîne de migrations.
- Export et import de fichier par le Storage Access Framework **d'abord**, Drive ensuite.
- WorkManager, rotation sur cinq fichiers, chiffrement optionnel.

**Terminé quand**

- Export, effacement complet, import : l'état est identique.
- Un test vérifie qu'aucune clé API ne figure dans le fichier produit.
- Drive et le fichier local sont deux implémentations du même port, interchangeables.

**Ordre volontaire** : le fichier local avant Drive. Il valide tout le format sans dépendre d'OAuth, et c'est lui qui garantit la réversibilité du projet.

---

## Les quatre décisions qu'on ne rattrape pas

Tout le reste se corrige à peu de frais. Ces quatre-là contaminent l'ensemble du code si elles arrivent tard.

| | Décision | Pourquoi c'est irréversible en pratique |
|---|---|---|
| 1 | `Clock` injecté dès la première ligne | Jours, semaines, tendances : la moitié de cette application dépend du temps. Le rattraper, c'est rouvrir chaque fichier. |
| 2 | Migrations Room dès la première table | Le jour où trois semaines de repas sont sur un vrai téléphone, il est trop tard pour prendre l'habitude. |
| 3 | Écran de validation générique en tranche 2 | Sinon quatre écrans divergents, et une généralisation à faire trois fois. |
| 4 | Aucune valeur de style hors du design system | Une règle ajoutée après 5 000 lignes ne s'applique pas rétroactivement : elle produit 200 avertissements qu'on finit par désactiver. |

## Sur la dette technique

Viser zéro dette produit son propre défaut : l'abstraction préventive, le port avec une seule implémentation, l'interface « au cas où ». C'est de la dette aussi, simplement plus flatteuse.

L'objectif utile est différent : **de la dette choisie, écrite, et placée là où elle est peu coûteuse à défaire.** L'objectif codé en dur de la tranche 1 en est l'exemple — c'est un raccourci, il est documenté, et sa date de péremption est connue.

Quand un raccourci est pris, il reçoit une entrée `~ par défaut` dans [11](11-decisions.md). Trois lignes, et il cesse d'être un piège pour devenir une décision.

## Correspondance avec la feuille de route

| Tranches | Version du doc [10](10-qualite-et-livraison.md#feuille-de-route) |
|---|---|
| Itération 0 à tranche 4 | 0.1 |
| Tranche 5 | 0.2 |
| Tranche 6 | 0.3 |
| Tranche 7 | 0.4 |
| Tranche 8 | 0.5 |
