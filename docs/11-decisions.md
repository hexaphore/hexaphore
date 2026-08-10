# 11 — Journal des décisions

Les choix structurants, leur raison, et ce qu'ils coûtent. Une décision sans contrepartie explicitée n'est pas une décision, c'est une préférence.

Format : contexte, choix, alternatives écartées, conséquences. Court. Une décision future ajoute une entrée, elle ne réécrit pas les anciennes — savoir ce qu'on croyait à l'époque a de la valeur.

**Statut des entrées** : `✓ validée` par l'auteur du projet · `~ par défaut` retenue faute de contre-indication, ouverte à révision · `⊘ remplacée par Dxx` révisée depuis, texte conservé.

Une entrée remplacée n'est **jamais** supprimée ni réécrite. Savoir ce qu'on croyait à l'époque, et pourquoi le raisonnement a cédé, est ce qui permet de rejuger la question sur autre chose qu'une impression le jour où elle revient. Le marqueur dans le titre suffit à ne pas s'y tromper en lisant.

---

## D01 — Kotlin natif et Jetpack Compose · ✓ validée

**Contexte.** Application Android uniquement, forte dépendance à la caméra, au décodage de codes-barres et à une interface animée.

**Choix.** Kotlin + Jetpack Compose, natif.

**Écarté.** *Flutter* : ouvre iOS, mais caméra et ML Kit passent par des greffons tiers et la couche native reste à écrire pour Keystore et les widgets. *Compose Multiplatform* : même promesse, écosystème moins mûr sur les points sensibles ici. *React Native* : mal adapté au traitement d'image et à une interface fortement animée.

**Conséquences.** Un portage iOS demanderait une réécriture de l'interface. Le découpage en modules ([06](06-architecture.md)) garde `:domain` en Kotlin pur, donc réutilisable via Kotlin Multiplatform si la question se pose un jour. Le coût de sortie est limité à ce qui devrait de toute façon être réécrit.

---

## D02 — CIQUAL embarqué, Open Food Facts en ligne · ✓ validée

**Contexte.** Deux besoins qu'une source unique ne couvre pas : reconnaître un produit emballé par son code-barres, et trouver un plat générique par son nom.

**Choix.** CIQUAL 2025 en base SQLite dans l'APK pour la recherche ; API Open Food Facts pour les codes-barres, avec cache local permanent.

**Écarté.** *Open Food Facts seul* : la recherche de « lasagne » y renvoie des barquettes industrielles, et rien ne fonctionne hors-ligne. *Dump Open Food Facts complet* : APK de plusieurs centaines de mégaoctets, données figées. *FatSecret ou Nutritionix* : meilleure couverture de codes-barres, mais clé commerciale impossible à publier dans un dépôt libre.

**Conséquences.** +4 Mo d'APK. Recherche instantanée et hors-ligne. Environ 1 produit sur 10 absent d'Open Food Facts, compensé par la création manuelle qui conserve le code-barres. Obligations de licence documentées en [04](04-sources-de-donnees.md#licences-et-obligations).

---

## D03 — L'IA extrait, la base calcule · ✓ validée

**Contexte.** Deux façons d'exploiter une photo : demander directement les macros au modèle, ou lui demander seulement d'identifier les aliments.

**Choix.** Le modèle ne rend qu'une liste `{aliment, quantité, unité, confiance}`. Les macros viennent de CIQUAL ou d'Open Food Facts, avec un repli sur estimation IA uniquement pour les lignes sans correspondance.

**Écarté.** *Macros directement par le modèle* : plus simple, plus souple sur les plats exotiques, mais deux analyses de la même assiette donnent deux résultats différents, et aucun chiffre n'est traçable. *Les deux côte à côte* : le plus transparent, mais double les appels et charge l'écran de validation.

**Conséquences.** Résultats reproductibles et sourçables, sortie de modèle 4 fois plus courte donc moins chère. En contrepartie, une étape de résolution à écrire et à régler ([04](04-sources-de-donnees.md#résolution--du-texte-de-lia-à-un-aliment)), et une dépendance à la qualité de la correspondance textuelle.

---

## D04 — Objectifs versionnés plutôt que mis à jour en place · ✓ validée

**Contexte.** Un objectif change : recalcul, édition manuelle, ajustement hebdomadaire. Une journée passée doit-elle être jugée sur l'objectif d'aujourd'hui ?

**Choix.** Non. Chaque modification crée une ligne dans `goal`, l'ancienne reçoit une date de fin. Une journée est toujours comparée à l'objectif actif ce jour-là.

**Écarté.** *Mise à jour en place* : une table plus simple, mais le calendrier se repeint entièrement à chaque changement d'objectif, et l'historique devient incompréhensible.

**Conséquences.** Une jointure sur toute lecture de journée passée, absorbée par un index. Historique des changements de cap gratuit, et retour arrière naturel.

---

## D05 — Les entrées de journal figent leurs valeurs · ✓ validée

**Contexte.** Une entrée référence un aliment. Si l'aliment change, l'entrée doit-elle suivre ?

**Choix.** Non. Les six macros sont copiées dans `food_entry` à l'enregistrement. Le lien vers l'aliment ne sert plus qu'à la provenance et au ré-ajout.

**Écarté.** *Normalisation stricte* : base plus propre, mais un fabricant qui reformule son produit réécrit un journal vieux de six mois, et supprimer un aliment amputerait l'historique.

**Conséquences.** Environ 40 octets par entrée, soit moins de 250 Ko par an. Un journal alimentaire est un registre d'événements : ce qui est écrit est écrit.

---

## D06 — Repas nommés plutôt que liste chronologique · ⊘ remplacée par D31

**Choix.** Petit-déjeuner, déjeuner, dîner, collation, avec sous-totaux, renommables et extensibles.

**Écarté.** *Liste chronologique* : une décision de moins à la saisie, mais on perd les sous-totaux et les repas favoris réutilisables — qui sont le principal levier de rapidité de saisie. *Mode hybride* : deux affichages à concevoir, à tester et à maintenir pour un gain marginal.

**Conséquences.** Le repas est pré-sélectionné selon l'heure, donc la décision supplémentaire coûte zéro tap dans le cas courant.

> **Remplacé par [D31](#d31--un-plat-pas-un-repas-nommé--validée).** Le raisonnement tenait tant qu'on regardait les sous-totaux ; à l'usage, la case à choisir arrive avant l'enregistrement et ne sert à rien. Le regroupement par plat donne les mêmes sous-totaux sans la question.

---

## D07 — Une couleur par macro · ✓ validée

**Choix.** Six teintes néon, stables dans toute l'application, celle des sucres dérivée de celle des glucides pour matérialiser l'inclusion.

**Écarté.** *Cyan/magenta seuls* : plus identitaire, moins lisible d'un coup d'œil. *Coloration selon l'atteinte (rouge → vert)* : parlante sur le calendrier, mais moralisante sur la journée en cours et inutilisable en daltonisme.

**Conséquences.** La couleur devient porteuse d'information, donc elle ne peut plus être utilisée pour décorer. Contrainte d'accessibilité assumée : la couleur ne porte jamais seule une information ([08](08-design-system.md#daltonisme)).

---

## D08 — Objectif adaptatif, appliqué sur accord seulement · ✓ validée

**Choix.** L'application compare la tendance de poids réelle à la trajectoire visée et **propose** un ajustement borné à ±150 kcal. Elle ne l'applique jamais d'elle-même.

**Écarté.** *Objectif figé* : simple, mais dérive à mesure que le métabolisme baisse avec le poids perdu. *Ajustement automatique* : l'objectif changerait sans que l'utilisateur comprenne pourquoi, ce qui détruit la confiance dans l'outil.

**Conséquences.** Nécessite un journal de poids et une logique de tendance. Des conditions de déclenchement strictes (adhérence, persistance) évitent les suggestions absurdes ([03](03-nutrition-calculs.md#adaptation-hebdomadaire)).

---

## D09 — Deux variantes de distribution · ✓ validée

**Contexte.** Le règlement du Play Store interdit les liens de don externes hors association reconnue.

**Choix.** Deux `productFlavors`. Le lien de don n'est **compilé** que dans la variante GitHub.

**Écarté.** *Masquage à l'exécution* : le lien reste dans l'APK et reste détectable à l'examen. *Renoncer au Play Store* : coupe la majorité des utilisateurs potentiels. *Renoncer au don* : inutile, la variante GitHub n'a aucune contrainte.

**Conséquences.** Une dimension de variante, une interface `DonationLinkProvider` à deux implémentations. Politique de confidentialité et formulaire Data Safety obligatoires pour la variante Play.

---

## D10 — Licence GPL-3.0 · ~ par défaut

**Choix.** GPL-3.0 pour le code.

**Raison.** Le copyleft garantit qu'une reprise du projet reste libre — cohérent avec l'esprit d'Open Food Facts, dont les données sont sous ODbL, une licence à partage à l'identique. Une application financée par les dons et bâtie sur des données communautaires a peu à gagner à autoriser une reprise propriétaire.

**Écarté.** *MIT / Apache-2.0* : adoption plus large et contributions d'entreprise facilitées, au prix de la possibilité qu'un fork fermé et monétisé s'appuie sur ce travail.

**À trancher.** Si l'objectif est la diffusion maximale du code plutôt que la protection du projet, Apache-2.0 est le meilleur choix. **Décision réversible tant qu'aucun contributeur externe n'a poussé de code** — après, il faut l'accord de chacun. C'est donc à figer avant la première contribution.

---

## D11 — Photos supprimées immédiatement · ~ par défaut

**Choix.** L'image est écrite dans le cache, envoyée, puis supprimée dans un bloc `finally`. Elle n'entre jamais dans la galerie et ne peut pas être revue.

**Raison.** L'énoncé initial excluait les photos du stockage. Une photo de repas est une donnée intime ; ne pas la conserver supprime toute la question de sa protection.

**À trancher.** Une vignette locale (≈ 30 Ko, purgeable, jamais sauvegardée) permettrait de revoir un repas et de relancer une analyse ratée sans reprendre la photo. C'est un vrai confort. Le modèle de données l'accueillerait sans migration (une colonne `thumbnail_path` nullable). **Dis-le si tu le veux : c'est à décider avant la 0.3.**

---

## D12 — Aucun serveur, jamais · ✓ validée

**Choix.** Pas de backend. Les clés API partent du téléphone vers le fournisseur ; les sauvegardes vont sur le Drive de l'utilisateur.

**Conséquences.** Pas de compte, pas de coût d'hébergement, pas de surface d'attaque, pas de RGPD à gérer côté serveur. En contrepartie : pas de synchronisation temps réel entre appareils, et aucune possibilité de mutualiser un cache d'analyses.

Cette décision conditionne toutes les autres. Toute fonctionnalité qui exigerait un serveur est hors périmètre par construction, et pas seulement hors v1.

---

## D13 — Nom : Hexaphore · ✓ validée

**Contexte.** Le premier nom envisagé, *Macronaut*, était déjà pris : domaine `macronaut.app` enregistré et identifiant `com.macronaut` publié sur le Play Store. Le second candidat a donc été vérifié avant d'être adopté.

**Choix.** **Hexaphore**. *Hexa* = six, soit exactement le nombre de compteurs de l'application — le nom porte sa propre justification.

**Vérifications effectuées.** Aucun logiciel de ce nom. `github.com/hexaphore` libre. Homonymes sans rapport et sans conflit de classe : une photographie de Jean-Pierre Sudre (1964), une lampe d'artisan, et une société indienne « Hexaphor Technologies » — orthographe différente, activité différente.

**Reste à vérifier avant la 1.0.** Disponibilité de `hexaphore.app`, absence de marque déposée à l'INPI en classes 9 et 42, absence d'application homonyme sur le Play Store.

**Leçon retenue.** Un nom se vérifie sur quatre fronts simultanément — dépôt, domaine, magasin d'applications, registre des marques — **avant** d'écrire la première ligne. Un seul des quatre suffit à tout invalider.

---

## D14 — Domaine et publication reportés après la 0.5 · ✓ validée

**Contexte.** Rien n'oblige à acheter un domaine ni à ouvrir un compte Play pour développer. La question est de savoir ce que ce report coûte.

**Choix.** Construire d'abord. Le domaine et le compte développeur n'interviennent qu'une fois les bases solides.

**Ce que le report ne coûte rien.** Le compte Play (25 $) n'apporte rien tant qu'il n'y a rien à publier, et l'ouvrir tôt ne contourne pas la règle des 12 testeurs. La politique de confidentialité, le formulaire Data Safety et les métadonnées Fastlane ne servent qu'à la publication.

**Ce que le report coûte.** Un risque unique : que `hexaphore.app` soit enregistré par quelqu'un d'autre entre-temps. Conséquence limitée — l'`applicationId` n'est verrouillé qu'à la première publication, donc un renommage resterait un simple remaniement mécanique. Le coût est une demi-journée de refactorisation, pas une impasse.

**Mesure de précaution immédiate, gratuite.** Réserver l'**organisation GitHub `hexaphore`** aujourd'hui. Deux minutes, zéro euro, et c'est exactement l'étape qui a manqué pour Macronaut.

**Conséquences.** La feuille de route de [10](10-qualite-et-livraison.md#feuille-de-route) sépare désormais la 1.0 (application finie, distribuée en APK) de la publication sur le Play Store, qui devient une étape ultérieure et facultative.

---

## D15 — Chaîne de construction alignée sur l'outillage installé · ~ par défaut

**Contexte.** L'itération 0 doit choisir un couple Gradle / AGP / Kotlin. Deux paliers existent : le courant (Gradle 9.6, AGP 9.3, Kotlin 2.4) et celui que comprend l'Android Studio installé sur la machine de développement, Ladybug 2024.2.1, qui refuse de synchroniser un projet en AGP 9.

**Choix.** Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, compileSdk 35.

**Ce qui a réellement tranché.** Pas l'IDE : detekt. La seule ligne stable de detekt est la 1.23, publiée pour Gradle 8 et Kotlin 2.0 ; la 2.0 n'existe qu'en `alpha`, sous un autre identifiant de groupe (`dev.detekt`) et avec une API de règles différente. Or les trois règles personnalisées de [10](10-qualite-et-livraison.md#analyse-statique) ne sont pas négociables. Le palier courant aurait donc imposé soit d'abandonner detekt, soit de bâtir l'outillage qualité du projet sur une version alpha. Que ce palier corresponde aussi à l'IDE installé est une coïncidence commode, pas la raison.

**Écarté.** *Palier courant* : oblige à mettre à jour Android Studio et laisse detekt sans version stable. *Palier plus ancien* : aucun gain.

**Conséquences.** Le catalogue de versions rend la montée mécanique : cinq lignes dans `gradle/libs.versions.toml`. **À rejuger quand detekt 2.0 sera stable** — c'est le seul événement qui débloque le reste de la chaîne.

---

## D16 — Les règles detekt vivent dans un build inclus · ~ par défaut

**Contexte.** Les trois règles personnalisées demandent un artefact Kotlin compilé contre `detekt-api`. L'itération 0 n'autorise que trois modules.

**Choix.** Un build composite `build-logic`, déclaré par `includeBuild` et non par `include`. `settings.gradle.kts` continue de ne lister que `:app`, `:domain` et `:core:designsystem`.

**Raison.** Ces règles s'exécutent sur la JVM de Gradle, pas sur un téléphone. Les mettre dans le graphe de dépendances de l'application mélangerait deux cycles de vie qui n'ont rien à voir. Le build inclus lit le même `libs.versions.toml`, donc la règle du catalogue unique reste vraie.

**Conséquences.** Une racine Gradle de plus dans le dépôt. Le `check` racine dépend explicitement de `:detekt-rules:check` : sans ce lien, les tests des règles ne tourneraient jamais, et une règle non testée est une règle qu'on croit active.

---

## D17 — Configuration detekt par surcouche de fichiers, pas par filtres de chemin · ~ par défaut

**Contexte.** Deux des trois règles ne concernent qu'un module : les imports Android pour `:domain`, les couleurs pour tout sauf `:core:designsystem`. La façon idiomatique est un filtre `includes` / `excludes` en motif glob.

**Choix.** Trois fichiers — `detekt.yml` commun, plus `detekt-domain.yml` et `detekt-designsystem.yml` — combinés selon le module.

**Raison.** Les motifs glob de detekt s'appliquent à des chemins, et un chemin Windows ne se sépare pas comme un chemin Linux. Une règle qui ne se déclencherait qu'en CI ne protège personne pendant qu'on écrit le code. Le filtre de la troisième règle porte pour la même raison sur un **nom de fichier**, jamais sur un chemin.

**Conséquences.** La configuration se lit dans trois fichiers au lieu d'un. En contrepartie, ce qui est actif dans quel module est explicite plutôt que déduit d'un motif.

---

## D18 — Deux ambiguïtés du design system, tranchées · ⊘ en partie remplacée par D25

Le document [08](08-design-system.md) se contredit sur deux points mineurs. Les deux sont tranchées en faveur de la règle la plus structurante.

**L'ambre du `SourceBadge`.** Le document dit que la pastille de source est monochrome *parce que les six couleurs sont réservées aux macros*, puis que « Estimation IA » s'affiche **en ambre** — qui est la teinte des lipides. Retenu à ce stade : un rôle `warning` distinct, défini dans `NeonTheme`, visuellement ambré mais indépendant de la palette des macros.

> **Remplacé par [D25](#d25--lestimation-ia-se-signale-par-une-forme-pas-par-une-couleur--validée).** Une septième couleur restait une septième couleur. Le badge se distingue désormais par la forme.

**Le fond du `NeonButton` principal.** Le document interdit le texte foncé sur aplat néon, puis décrit le bouton principal comme portant « un fond dégradé plein ». Un aplat néon plein imposerait précisément un texte foncé. Retenu : un dégradé de la teinte à faible opacité sur le fond sombre. Le bouton se distingue nettement des boutons à contour, et le néon reste l'élément clair de la paire.

---

## D19 — `Clock` et `DispatcherProvider` : port dans `:domain`, implémentation dans `:app` · ~ par défaut

**Contexte.** [06](06-architecture.md) place ces deux abstractions dans `:core:common` ; [12](12-plan-de-developpement.md) les demande dans `:domain` dès la tranche 1. Aucun des deux modules d'accueil n'existe encore.

**Choix.** Les interfaces vont dans `:domain` — ce sont des ports, et un port appartient au métier. `SystemClock` et `DefaultDispatcherProvider` vont dans `:app`, faute d'ailleurs.

**Dette assumée, datée.** Ces deux implémentations déménagent dans `:core:common` le jour où ce module naît, c'est-à-dire quand il aura un second fichier à contenir. Le déplacement est mécanique : deux fichiers et une ligne dans `config/detekt/detekt.yml`, où `SystemClock.kt` est nommément autorisé à lire l'horloge.

> **Réglée en tranche 1.** `:core:common` existe, les deux implémentations y sont, et le module Hilt qui les lie a suivi. La règle detekt n'a pas eu à bouger : elle filtre sur un nom de fichier et non sur un chemin, ce qui a rendu le déménagement invisible pour elle.

**`DispatcherProvider` n'a aucun appelant** à ce stade. C'est une exception délibérée au refus de l'abstraction préventive : il figure parmi les décisions que [12](12-plan-de-developpement.md) désigne comme non rattrapables, et l'ajouter tard ne coûte pas une refonte mais une centaine d'appels à corriger un par un.

---

## D20 — Inter embarquée, et rien d'autre en ressource de police · ~ par défaut

**Choix.** La police variable Inter (`Inter[opsz,wght].ttf`, SIL Open Font License 1.1) est versionnée dans `:core:designsystem`, et les quatre graisses utilisées sont dérivées de l'axe `wght`.

**Raison.** Un seul fichier au lieu de quatre statiques, et surtout aucun appel réseau à un service de polices — cohérent avec « aucun trafic sortant non déclaré ». Les axes variables demandent l'API 26, ce qui est exactement le minimum du projet.

**Conséquences.** +860 Ko dans l'APK avant compression. La licence est versionnée dans `third-party-licenses/`, et l'écran « À propos » devra la citer au même titre que CIQUAL et Open Food Facts.

---

## D21 — Ce que l'itération 0 ne construit pas · ~ par défaut

Trois éléments décrits ailleurs dans la documentation sont volontairement absents du socle. Ils sont listés ici pour cesser d'être des oublis.

| Absent | Raison | Quand |
|---|---|---|
| Les deux `productFlavors` `github` / `play` | Elles n'existent que pour compiler ou non le lien de don ([D09](#d09--deux-variantes-de-distribution--validée)). Aucune ligne de code ne les distingue encore, et une dimension de variante double le nombre de tâches Gradle. | Avec `DonationLinkProvider` |
| Tests d'image et rapport de couverture en CI | [10](10-qualite-et-livraison.md#intégration-continue) les prévoit dans le pipeline. Il n'y a rien à couvrir ni à figer : `:domain` ne contient que des interfaces. | Tranche 1 |
| Les plugins de convention Gradle | Trois modules ne justifient pas une couche d'indirection pour dix lignes de configuration partagée. Elle est posée dans le `build.gradle.kts` racine. | Vers le sixième module |

> **Échéance atteinte.** Les deux premières lignes restent d'actualité. La troisième est réglée par [D37](#d37--plugins-de-convention-gradle--validée) : le projet compte huit modules, et le bloc partagé était recopié cinq fois.

---

## D22 — Style ktlint : `intellij_idea`, pas `ktlint_official` · ~ par défaut

**Contexte.** ktlint propose deux styles. `ktlint_official` est le sien ; `intellij_idea` est celui que produit le formatage automatique d'Android Studio.

**Choix.** `intellij_idea`, déclaré dans `.editorconfig`.

**Raison.** Deux, dont une décisive. D'abord, avec `ktlint_official`, un `Ctrl+Alt+L` dans l'IDE **crée** des violations : l'outil de formatage et l'outil de vérification ne sont pas d'accord, et c'est le développeur qui arbitre dix fois par jour. Ensuite, `ktlint_official` éclate `class Depot @Inject constructor(...)` sur quatre lignes indentées, en décalant tout le corps de la classe. Sur un projet où presque chaque classe a un constructeur injecté, c'est une taxe de lisibilité permanente pour un gain nul.

**Conséquences.** Quelques règles de mise en forme en moins (`function-signature`, `class-signature`, `multiline-expression-wrapping`). Aucune ne portait sur autre chose que le placement des retours à la ligne.

**Convention d'écriture qui va avec.** Le français **accentué** dans tout le Kotlin — KDoc, commentaires, chaînes affichées — parce que c'est de la documentation et que le compilateur lit en UTF-8. L'**ASCII** dans les fichiers d'outillage (Gradle, YAML, XML, `.properties`) et dans les messages imprimés par Gradle ou detekt : un fichier `.properties` est spécifié en Latin-1, et une console Windows n'est pas en UTF-8 par défaut. La frontière est donc « ce que lit le compilateur Kotlin » contre « ce que lit ou affiche l'outillage », et pas une préférence.

---

## D23 — Recherche dès le 2ᵉ caractère, après une pause de frappe · ✓ validée

**Contexte.** [01](01-perimetre.md#critères-dacceptation-de-la-v1) exigeait des résultats « dès le 3ᵉ caractère », [02](02-parcours-et-ecrans.md#modale--recherche) et [12](12-plan-de-developpement.md) « à partir du 2ᵉ ». Contradiction franche, dans le document qui définit les critères d'acceptation.

**Choix.** **Deux caractères**, et la requête part **120 ms après la dernière frappe**, pas à chaque touche. Une frappe qui arrive avant l'échéance annule la précédente.

**Pourquoi deux et pas trois.** La recherche est locale : le coût d'une requête inutile est une lecture SQLite, pas un aller-retour réseau. Et deux caractères suffisent pour « riz », « thé », « œuf » — des aliments courants qu'un seuil à trois rendrait introuvables tant que le mot n'est pas fini.

**Pourquoi l'attente.** Sans elle, taper « chocolat » lance huit recherches dont sept sont jetées, et les résultats clignotent pendant la frappe. L'anti-rebond n'est pas une optimisation : c'est ce qui rend la liste lisible pendant qu'on écrit.

**Conséquences.** Le budget de 150 ms de [01](01-perimetre.md) se compte **à partir de la fin de l'anti-rebond**. Les deux délais s'additionnent à l'usage (≈ 270 ms au pire), et c'est assumé : l'un est une contrainte de performance, l'autre un choix d'ergonomie.

---

## D24 — Les fibres sont déduites du solde glucidique · ✓ validée

**Contexte.** [03](03-nutrition-calculs.md) pose les facteurs d'Atwater — dont **fibres 2 kcal/g** — puis calcule les glucides en solde sans retirer les fibres. Sur l'exemple de référence, 35 g de fibres représentent **70 kcal distribuées deux fois**. Le document présentait cet écart comme « quelques kcal » d'arrondi ; ce n'en était pas un.

**Choix.** L'ordre de calcul devient : protéines, lipides, **fibres**, puis glucides en solde.

```
glucides = (kcal − 4 × protéines − 9 × lipides − 2 × fibres) / 4
```

**Écarté.** *Ne pas compter l'énergie des fibres* : cohérent avec certaines conventions, mais incohérent avec CIQUAL et Open Food Facts, qui appliquent le règlement UE 1169/2011. Les valeurs saisies et les objectifs n'auraient plus parlé la même langue. *Inclure les fibres dans les glucides* : c'est la convention américaine ; elle contredirait les deux sources de données du projet.

**Conséquences.** L'exemple de référence passe de 330 g à **312 g** de glucides. Les fibres ne sont jamais réduites pour dégager des glucides — leur plancher de 25 g est un besoin, pas une variable d'ajustement. Le test de référence de `GoalCalculatorTest` gagne un **contrôle de cohérence énergétique** : c'est lui qui aurait attrapé l'erreur.

---

## D25 — L'estimation IA se signale par une forme, pas par une couleur · ✓ validée

**Contexte.** [08](08-design-system.md) demandait un badge « Estimation IA » en ambre, tout en réservant les six teintes aux macros. **D18** avait proposé une septième couleur dédiée.

**Choix.** Aucune couleur. Tous les `SourceBadge` sont neutres. `Estimation IA` porte un **contour en pointillés** et un **glyphe en vague** de 16 dp.

**Raison.** Une septième couleur reste une septième couleur : elle occupe l'espace chromatique, elle demande une déclinaison claire et sombre, et elle finit par ressembler à l'ambre des lipides sur un écran mal calibré. Surtout, la règle de daltonisme du projet interdit qu'une couleur porte seule une information — un badge coloré aurait donc eu besoin d'un second canal de toute façon. Autant n'avoir que celui-là.

**Pourquoi le pointillé plutôt qu'une icône d'alerte.** Un triangle d'avertissement dit « attention, problème ». Une estimation n'est pas un problème, c'est une valeur moins précise. Un contour discontinu et une vague le disent sans dramatiser, et sans légende.

**Conséquences.** Le rôle `warning` et tout le mécanisme `NeonExtendedColors` disparaissent du thème : Material 3 suffit. Le design system revient à exactement six teintes plus les fonds, ce qu'annonçait [D07](#d07--une-couleur-par-macro--validée).

---

## D26 — Le `User-Agent` d'Open Food Facts est figé · ✓ validée

**Contexte.** [04](04-sources-de-donnees.md) exigeait un `User-Agent` obligatoire tout en laissant un `<compte>` non résolu. Or Open Food Facts bloque les clients anonymes, et le symptôme ressemble à une panne réseau — le piège est signalé dans [12](12-plan-de-developpement.md) et resterait armé.

**Choix.** `Hexaphore/<version> (github.com/hexaphore/hexaphore)`, l'organisation réservée en [D14](#d14--domaine-et-publication-reportés-après-la-05--validée).

**Raison.** Une organisation survit à un changement de propriétaire, à un passage en association et à un départ de son auteur ; un compte personnel, non. La version vient du `versionName`, pour qu'un signalement d'Open Food Facts désigne un binaire précis plutôt que « l'application ».

---

## D27 — Objectif ou limite : la nature appartient à la macro · ⊘ en partie remplacée par D47

**Contexte.** Constaté sur appareil : les six jauges se ressemblent trop. [08](08-design-system.md) ne distinguait qu'un seul plafond, les sucres, et le reste se remplissait de la même façon. Une jauge de sucres qui monte ressemble alors à une réussite — le contresens exact que la distinction était censée empêcher.

**Choix.** Trois objectifs — **calories, protéines, fibres** — et trois limites — **glucides, sucres, lipides**. La nature est portée par l'énumération `Macro` dans `:domain`, pas par un paramètre de composant.

**Pourquoi dans le domaine.** C'est une règle nutritionnelle, pas un choix d'affichage. Tant qu'elle est un paramètre, elle est un oubli possible : il suffit qu'un écran instancie une barre de sucres sans le préciser pour que le contresens revienne. Portée par la macro, la question ne se pose plus.

**Pourquoi les lipides du côté des limites.** Ils ont bien un plancher physiologique de 0,6 g/kg, mais il est appliqué **au calcul de l'objectif**, une fois pour toutes. Au jour le jour, l'utilisateur n'a rien à atteindre : il a un budget à ne pas dépasser.

**Conséquences.** Trois signaux redondants distinguent les deux familles : le suffixe `max` sur la valeur, le comportement de la jauge, et la phrase annoncée par TalkBack — « sur un objectif de » contre « sur une limite de ». Le paramètre `mode` de `MacroBar` disparaît de l'API publique : il n'y avait aucune raison légitime de le forcer.

> **Le second signal est remplacé par [D47](#d47--les-six-macros-brillent--en-partie-remplacée-par-d48).** La nature reste portée par la macro, et c'est l'essentiel de cette entrée. Ce qui cède est l'**extinction** de la jauge sous le seuil : constaté sur appareil, trois macros allumées et trois éteintes se lisent comme un défaut d'affichage. Le repère de seuil et l'échelle élargie restent.

---

## D28 — Un bouton indisponible réagit quand même · ✓ validée

**Contexte.** Constaté sur appareil : appuyer sur un bouton grisé ne produit strictement rien, et on ne sait pas si l'application a reçu l'appui ou si elle est figée.

**Choix.** `NeonButton` distingue désormais trois disponibilités au lieu d'un booléen : **disponible**, **indisponible**, **désactivé**. Un bouton *indisponible* est grisé et sans lueur au repos, mais il réagit à l'appui — réduction d'échelle, lueur brève — puis appelle son action, à qui il revient d'expliquer ce qui manque.

**Raison.** Le cas existait déjà dans la spécification sans avoir de support : [02](02-parcours-et-ecrans.md#modale--photo) demande que les modes IA sans clé restent « visibles mais grisés ; un tap ouvre une explication courte ». Un booléen `enabled` ne pouvait pas exprimer ça.

**Écarté.** *Masquer le bouton* : laisse croire que la fonctionnalité n'existe pas — le document l'excluait déjà. *Garder un seul état éteint* : il faut bien pouvoir rendre un bouton réellement inerte pendant qu'une action est en cours, et l'annoncer comme tel au lecteur d'écran.

**Conséquences.** Un état de plus à choisir à chaque appel. En contrepartie, le choix est explicite : `DISABLED` engage à ce qu'il n'y ait rien à expliquer. TalkBack annonce « indisponible » sur le second état, sans quoi il se présenterait comme un bouton ordinaire.

---

## D29 — Un total incomplet se signale au lieu de se taire · ✓ validée

**Contexte.** `null` signifie *inconnu* et jamais zéro ([04](04-sources-de-donnees.md#le-piège-des-valeurs-textuelles)). La règle est claire pour une ligne isolée ; elle ne dit rien de ce qui arrive quand on en additionne dix. Or c'est là qu'elle se perd : additionner des `null` comme des zéros donne un nombre parfaitement plausible, et plus rien ensuite ne permet de savoir qu'il est faux.

**Choix.** Un cumul n'est pas un nombre mais un couple : la somme de ce qu'on sait, et un drapeau `complete` qui tombe dès qu'une seule ligne du cumul avait une valeur inconnue. `MacroTotals.of` le calcule ; il n'existe pas d'autre chemin pour totaliser des macros.

**Écarté.** *Rendre le total nullable* : une journée entière deviendrait « inconnue » parce qu'un seul produit ne déclare pas ses fibres, alors que le total partiel reste utile. *Ne rien signaler* : c'est l'erreur que [12](12-plan-de-developpement.md) désigne comme la plus difficile à repérer — elle fausse des mois de journal en silence.

**Conséquences.** L'interface doit dire qu'un total est minoré ; un chiffre affiché sans mention vaut promesse d'exactitude. Une liste vide, elle, rend des totaux **complets** à zéro : ne rien avoir noté est une information exacte, pas une lacune. Trois tests couvrent ces trois cas.

---

## D30 — Objectif provisoire en dur, avec sa date de péremption · ~ par défaut

**Contexte.** L'accueil de la tranche 1 doit afficher six objectifs. Leur calcul réel demande un profil, un poids cible et une échéance, qui n'existent qu'en tranche 4.

**Choix.** `DailyGoal.Placeholder` — 2 000 kcal et sa répartition, dans `:domain`.

**Ce n'est pas un chiffre arbitraire.** La répartition suit les règles de [03](03-nutrition-calculs.md) pour un maintien à 2 000 kcal sur un poids de référence de 70 kg : protéines 1,6 g/kg, lipides 30 % des calories, fibres 14 g pour 1 000 kcal, glucides en solde une fois les fibres déduites. Le contrôle de cohérence retombe à 1 kcal près. L'appliquer ici sert aussi de première vérification de [D24](#d24--les-fibres-sont-déduites-du-solde-glucidique--validée).

**Date de péremption.** Sa disparition est un critère de fin de la tranche 4, déjà écrit dans [12](12-plan-de-developpement.md). Un seul point du code le référence, et le nom `Placeholder` le désigne comme tel dans chaque complétion de l'IDE.

---

## D31 — Un plat, pas un repas nommé · ✓ validée

**Contexte.** **D06** avait retenu quatre repas nommés — petit-déjeuner, déjeuner, dîner, collation. Vérifié sur l'accueil réel, le choix ne tient pas : la catégorie doit être fixée **avant** d'enregistrer, pour répondre à une question que personne ne se pose. Ce qui compte est ce qu'on a mangé aujourd'hui, pas à quel repas.

**Choix.** L'unité de saisie est le **plat** : plusieurs aliments, entrés en une fois. Aucune catégorie, aucun nom obligatoire. Les plats s'affichent dans l'ordre où ils ont été notés, et l'heure les situe.

**Ce que ça ne coûte pas.** Les sous-totaux, qui étaient l'argument principal de D06, restent : ils sont désormais ceux du plat. Les plats favoris réutilisables aussi — un favori est un plat enregistré, pas un repas nommé.

**Ce que ça coûte.** Le tri chronologique remplace un ordre fixe : deux plats notés dans le désordre s'affichent dans le désordre. C'est le comportement attendu d'un journal.

**Conséquences.** La table `meal` devient `dish` et perd `type`, `custom_name` et `sort_index` ; elle gagne `logged_at`. Le champ « repas de destination » disparaît de l'écran de validation, ce qui lui retire une décision. Le réglage « renommer et ajouter des repas » disparaît des préférences.

---

## D32 — La source appartient au plat, et ne change jamais · ✓ validée

**Contexte.** [07](07-modele-de-donnees.md) portait deux sources sur chaque **ligne** : `entry_source` (par quel chemin elle est arrivée) et `nutrition_source` (d'où viennent ses chiffres). L'accueil affichait donc une pastille par aliment.

**Choix.** Une seule source, portée par le **plat**. On ne photographie pas une assiette aliment par aliment.

**Elle ne change jamais.** Un plat reste éditable à la main indéfiniment, mais son origine reste ce qu'elle était : c'est un fait historique, pas un état. Sans cela, corriger une quantité sur une proposition de l'IA la ferait passer pour une saisie manuelle, et on perdrait la seule trace de ce qui a été deviné.

**Ce qui disparaît.** `nutrition_source` par ligne. La distinction « ce chiffre vient de CIQUAL » contre « ce chiffre est une estimation » reste nécessaire — [05](05-ia.md#résolution) prévoit qu'un plat photographié résolve certaines lignes dans les bases et estime les autres — mais elle n'a **aucun porteur avant la tranche 6**, où le résolveur existera. La réintroduire maintenant serait une colonne que rien ne remplit. Elle reviendra sous la forme minimale qui suffit : un marqueur « estimée » par ligne, pas une seconde énumération de sources.

**Conséquences.** `SourceBadge` se pose une fois par plat. Un contenu **proposé** — photo ou description — porte le contour en pointillés ; une recherche, un code-barres ou une saisie manuelle non. Le badge devient donc lisible : sur l'ancien écran, cinq pastilles voisines ne distinguaient plus rien.

---

## D33 — Un hexagone en tête d'accueil, et un seul ordre angulaire · ✓ validée

**Contexte.** Le projet s'appelle Hexaphore parce qu'il tient six compteurs. Rien dans l'interface ne le montrait : l'accueil ouvrait sur un anneau de calories, c'est-à-dire sur **un** compteur, les cinq autres relégués en barres.

**Choix.** `MacroHexagon` remplace l'anneau en tête d'accueil. Six quartiers de 60°, hexagone à sommet plat, chaque macro remplissant sa part depuis le centre, le contour marquant l'objectif. Spécification complète en [08](08-design-system.md#macrohexagon).

**Les barres restent.** L'hexagone donne la forme d'un coup d'œil ; il ne peut pas dire « 87 / 144 g », ni le `max` d'une limite, ni le `≥` d'un total minoré. Les deux ne se concurrencent pas, ils répondent à deux questions différentes : *comment va ma journée* et *combien exactement*.

**Écarté.** *Remplacer aussi les barres* : il aurait fallu réintroduire les chiffres en étiquettes autour de la figure, ce qui ne tient pas à 200 % de police. *Ajouter l'hexagone au-dessus de l'anneau* : la même information dite trois fois.

**Le dépassement rétrécit la figure.** Le contour de l'objectif n'est pas la limite du dessin ; c'est le dessin entier qui se met à l'échelle pour que le plus grand débordement tienne. L'hexagone cible qui rapetisse **est** le signal. Plafonné à 200 % — sans quoi une saisie erronée à 2 000 % réduirait la cible à un point, précisément au moment où il faut la lire pour corriger. *(Plafond ramené à 150 % par [D47](#d47--les-six-macros-brillent--en-partie-remplacée-par-d48) : à 200 % la cible tombait à la moitié de sa taille, et un dépassement de moitié se voit déjà largement.)*

**Conséquence structurante : un seul ordre angulaire.** [08](08-design-system.md#daltonisme) fixait pour les pastilles du calendrier un ordre différent de celui demandé ici. Deux ordres pour les mêmes six macros annuleraient le bénéfice recherché : la position ne renseigne que si elle est la même partout. L'ordre de l'hexagone devient donc celui de toute l'application — **calories, protéines, fibres, glucides, sucres, lipides, sens horaire depuis le haut** — et les barres de l'accueil s'y alignent.

**Ce qui reste ouvert.** La pastille du calendrier devient-elle un mini-hexagone en tranche 7 ? À 44 dp, six quartiers restent probablement lisibles ; à 28 dp en vue mensuelle, sûrement pas. À trancher sur maquette, pas sur intuition.

---

## D34 — La table `food` attend la tranche qui la remplit · ~ par défaut

**Contexte.** [12](12-plan-de-developpement.md) liste `dish`, `food_entry` **et** `food` dans le contenu de la tranche 1.

**Choix.** Seules `dish` et `food_entry` sont créées. `food` naît en tranche 3, avec l'import CIQUAL qui la peuple.

**Raison.** Rien n'écrit dans `food` avant la tranche 3, et rien ne la lit : les entrées de journal figent leurs macros et n'ont pas besoin de la fiche d'origine pour s'afficher. Une table vide dans le schéma exporté n'est pas une préparation, c'est une ligne de plus à migrer le jour où sa vraie forme se révèle différente de celle qu'on avait devinée.

**Conséquences.** `food_entry.food_id` — le lien de provenance de [07](07-modele-de-donnees.md) — arrive avec elle, en colonne nullable. C'est précisément le type de migration que la règle de conception privilégie : *une colonne nullable plutôt qu'une table nouvelle, une table nouvelle plutôt qu'un renommage*.

---

## D35 — Le test de migration tourne sur la JVM, pas sur un appareil · ✓ validée

**Contexte.** `MigrationTestHelper` de Room est une règle **JUnit 4**, et le projet teste en JUnit 5. Le chemin habituel est le test instrumenté, sur émulateur.

**Choix.** Robolectric, plus JUnit 4 et le moteur *vintage* pour `:core:database` uniquement. Le test entre ainsi dans `./gradlew check` et dans la CI, sans appareil.

**Raison.** Un test de migration qu'il faut brancher un téléphone pour exécuter est un test qu'on n'exécute pas — et le seul moment où il compte est celui où on a oublié de le lancer.

**Écarté.** *Test instrumenté* : plus fidèle, mais absent de la CI et donc absent de la revue. *Se passer de `MigrationTestHelper`* en vérifiant seulement la présence du fichier de schéma : cela n'aurait rien éprouvé du mécanisme, seulement de l'export.

**Conséquences.** JUnit 4 revient dans le projet, cantonné à ce module. Deux moteurs de test cohabitent sous la même plateforme et la même commande.

---

## D36 — L'application démarre sur un journal vide · ✓ validée

**Contexte.** L'accueil était alimenté par un jeu de démonstration tant que Room n'existait pas. Room branché, fallait-il le garder pour que l'écran reste peuplé ?

**Choix.** Non. Le jeu de démonstration disparaît avec la bascule, et `:app` ne dépend plus de `:core:testing`.

**Raison.** Afficher des plats que l'utilisateur n'a pas notés serait mentir sur l'état de son journal — dans une application dont tout le propos est de dire la vérité sur ce qu'on a mangé, y compris quand la donnée manque. La journée vide est le comportement exact tant que la tranche 2 n'a pas apporté la saisie, et l'état vide de l'accueil est écrit pour ça.

**Ce que ça coûte.** L'hexagone ne se vérifie plus avec des données depuis l'accueil. Il se vérifie depuis la **galerie**, qui en montre trois cas dont un dépassement au plafond — c'est-à-dire mieux qu'un seul jeu figé.

---

## D37 — Plugins de convention Gradle · ✓ validée

**Contexte.** [D21](#d21--ce-que-litération-0-ne-construit-pas--par-défaut) reportait la question « vers le sixième module ». Il y en a huit. Le bloc `android { compileSdk / minSdk / compileOptions }` et `kotlin { jvmTarget }` était recopié **à l'identique dans cinq** `build.gradle.kts`, et le module suivant en aurait produit un sixième exemplaire.

Ce n'est pas la répétition qui coûte, c'est ce qu'elle rend possible : une divergence entre deux copies ne se voit qu'en compilant celle qui a divergé. Un module resté en `compileSdk 34` compile parfaitement — jusqu'à ce qu'un autre utilise une API de 35.

**Choix.** Six plugins dans `build-logic/convention` : `hexaphore.jvm.library`, `hexaphore.android.library`, `hexaphore.android.library.compose`, `hexaphore.android.application`, `hexaphore.android.hilt`, `hexaphore.android.feature`. `compileSdk` et `jvmTarget` n'apparaissent plus qu'une fois chacun, dans `Conventions.kt`. Les versions restent lues dans `gradle/libs.versions.toml` — aucun numéro n'est écrit dans `build-logic`.

**Des classes, pas des scripts précompilés.** Un fichier `hexaphore.android.library.gradle.kts` se lit mieux, et c'est ce qui a été écrit d'abord. Il ne fonctionne pas ici : un script précompilé résout les identifiants de son propre bloc `plugins { }` sur **son** chemin de classes d'exécution, ce qui obligerait à embarquer AGP dans `build-logic` en `implementation`. On se retrouve alors avec deux AGP — celui du build racine et celui du build inclus — et Gradle échoue sur `com.android.build.gradle.BaseExtension` sans dire lequel des deux il cherchait. Une classe applique par identifiant, résolu sur le chemin du module cible : un seul AGP, et `compileOnly` suffit à compiler contre son API.

**Ce que le build racine garde.** Les sept `alias(...) apply false` restent, et ils ne sont pas décoratifs : c'est eux qui posent AGP et Kotlin sur le chemin de classes du build racine — le seul que voient detekt et ktlint, appliqués par cross-configuration. Sans eux, ktlint cherche `BaseExtension` dans un chargeur de classes qui ne l'a pas.

**Deux `includeBuild` pour un seul build inclus.** Celui de `pluginManagement` résout les identifiants `hexaphore.*`. Celui de la racine substitue le projet local à la coordonnée `app.hexaphore.buildlogic:detekt-rules`. Contrairement à ce qu'on pourrait croire, le premier ne fait pas le second : sans la seconde ligne, Gradle va chercher les règles detekt maison sur Maven Central.

**L'analyse statique reste en cross-configuration.** Un plugin `hexaphore.quality` serait plus idiomatique — c'est même exactement ce que les plugins de convention sont censés remplacer. Il s'appliquerait module par module, donc **oublier de l'appliquer désactiverait detekt sur un module entier sans qu'aucun build n'échoue**. Le `subprojects { }` rend l'oubli impossible. La cohérence perd, la garantie gagne.

**Conséquences.** `feature/home/build.gradle.kts` passe de 60 lignes à 7. Le prochain `:feature` en coûtera autant. En contrepartie, ce qu'un module reçoit ne se lit plus dans son propre fichier : il faut ouvrir la convention. C'est le prix, et il est payé une fois pour toutes les huit.

---

## D38 — La galerie vit dans `src/debug`, et detekt l'y suit · ✓ validée

**Contexte.** La galerie des composants était dans `src/main` de `:app` alors que seule `GalleryActivity`, déclarée par la variante `debug`, l'utilise. R8 la supprimait de la release parce que rien ne la référençait — mais c'est une élimination qu'il fallait espérer, pas une garantie. Du code que la release n'atteint jamais n'a rien à faire dans son jeu de sources.

**Choix.** Les quatre fichiers et leurs chaînes passent en `src/debug`. `app/src/main/res/values/strings.xml` ne contient plus que `app_name`.

**Ce que le déplacement a révélé.** La tâche `detekt` par défaut n'analyse que `src/main` et `src/test`. Vérifié sur ce projet plutôt que supposé : un `Color(0xFF123456)` écrit dans `src/debug` passait l'analyse **sans un mot**, alors que la règle `HardcodedColor` est active. Déplacer 500 lignes les aurait donc sorties de la revue automatique, en silence et sans qu'aucun build ne change de couleur.

`Detekt.setSource(files("src"))` corrige le point pour tous les modules à la fois. La même sonde échoue désormais, avec le message de la règle.

**Conséquences.** Le jeu de sources d'un module n'a plus d'incidence sur ce qui est analysé. C'est ce qu'on croyait déjà, et c'est maintenant vrai. Le coût est nul : detekt lisait déjà tous les fichiers Kotlin du module, il en ignorait simplement une partie sur un critère de répertoire.

---

## D39 — Un échec de lecture se dit · ✓ validée

**Contexte.** `HomeUiState` n'avait que `Loading` et `Content`, et son KDoc l'assumait : « la lecture du journal ne peut pas échouer tant qu'elle vient de la mémoire ». Room a changé cette phrase sans que l'état d'écran suive. Un `SQLiteException` remontait donc dans un flux qui n'avait aucun moyen de le représenter.

**Ce que ça produisait.** Une journée vide. Or une journée vide n'est pas une absence de réponse, c'est une **affirmation** : « vous n'avez rien noté aujourd'hui ». Une application qui refuse de confondre `null` avec zéro sur une valeur de fibres ne peut pas confondre « je n'ai pas pu lire » avec « il n'y a rien » sur une journée entière.

**Choix.** `HomeUiState.Error`, sans détail. Une base illisible, un disque plein et un fichier corrompu appellent le même geste — réessayer — et un message plus précis n'apporterait que des mots dont personne ne peut rien faire. Un encart inline avec un bouton *Réessayer*, pas un dialogue : rien n'est détruit, rien n'est irréversible.

**Le point technique qui rend le bouton utile.** `catch` est placé **à l'intérieur** du `flatMapLatest`. Un flux qui a rattrapé une exception est terminé ; à l'extérieur, il ferait terminer la source de `stateIn`, qui resterait figée sur `Error` quoi qu'on pousse dans le déclencheur de relecture. Le bouton aurait alors existé sans rien faire — pire qu'absent, parce qu'il aurait promis quelque chose.

**Conséquences.** `InMemoryDiaryRepository` gagne un champ `failure`. Ce n'est pas du décor de test : sans lui, la seule façon d'éprouver ce cas serait de corrompre une vraie base. Deux tests couvrent l'échec et la reprise.

---

## D40 — Ce que la tranche 2 ne construit pas · ✓ validée

Trois éléments décrits ailleurs sont volontairement absents. Listés ici pour cesser d'être des oublis, comme [D21](#d21--ce-que-litération-0-ne-construit-pas--par-défaut) l'avait fait pour le socle.

| Absent | Raison | Quand |
|---|---|---|
| Le port `CustomFoodStore` et le formulaire d'aliment personnel | [D34](#d34--la-table-food-attend-la-tranche-qui-la-remplit--par-défaut) a reporté la table `food` en tranche 3. Un aliment personnel n'a de sens que réutilisable, donc trouvable : sans recherche, il serait écrit dans une table que rien ne lit, derrière un port à une seule implémentation. C'est exactement l'abstraction préventive que le projet refuse. | Tranche 3, avec `food` et la recherche |
| Le choix de la date sur l'écran de validation | La saisie est possible aujourd'hui et dans le passé, mais **aucun écran ne mène à un jour passé** avant la tranche 7. Un sélecteur de date servirait à corriger un champ que rien ne peut encore mal remplir. La date est affichée, et vient de l'horloge. | Tranche 7, avec l'écran Journée |
| La survie d'une saisie à la mort du processus | Le `ViewModel` couvre la rotation et le passage en arrière-plan, qui sont les cas fréquents. Persister le formulaire demande une représentation sérialisable des types du domaine, dans **chaque** écran de saisie — c'est un mécanisme transverse, pas une ligne à ajouter ici. Le construire pour un seul écran, c'est le construire deux fois. | Avec le deuxième écran qui a une saisie longue |

**Ce que ça ne coûte pas.** `docs/12` annonçait `CustomFoodStore` en tranche 2 ; la capacité annoncée par la tranche — « j'ajoute un aliment à la main » — reste entière. On saisit un nom, une quantité et des valeurs, et le plat entre au journal. Ce qui manque est la **réutilisation** de cet aliment, qui est le sujet de la tranche 3.

---

## D41 — `IdGenerator`, port comme l'horloge · ✓ validée

**Contexte.** Les identifiants sont des UUIDv4 générés côté application ([07](07-modele-de-donnees.md)). Un `UUID.randomUUID()` écrit au milieu de `LogDish` aurait été le chemin court.

**Choix.** Un port `IdGenerator` dans `:domain`, `UuidGenerator` dans `:core:common`, une génération séquentielle dans `:core:testing`.

**Raison.** Exactement celle de `Clock` : une entrée non déclarée rend le résultat invérifiable. Sans le port, un test de `LogDish` ne peut affirmer que « un plat a été enregistré » ; avec lui, il affirme « ce plat-là, avec ces lignes-là, rattachées à ce plat-là ». Le lien entre un plat et ses lignes est précisément ce qu'une erreur d'écriture casserait en silence.

**Ce qui écarte le soupçon d'abstraction préventive.** Deux implémentations existent le jour où le port naît, et la seconde n'est pas un décor : elle est ce qui rend les quatre tests d'écriture possibles.

**Écarté.** *Générer les identifiants dans l'adaptateur Room*, comme une base attribuerait une clé. Défendable, mais le domaine ne pourrait alors plus construire un `Dish` complet, et `LogDish` rendrait un identifiant qu'il n'a pas choisi — la logique « ce que devient un brouillon » se serait déplacée dans la couche de persistance.

---

## D42 — Une ligne de brouillon porte des valeurs absolues · ~ par défaut

**Contexte.** [02](02-parcours-et-ecrans.md#écran-de-validation-dentrée) demande que « les macros se recalculent en direct » quand la quantité change. Ce recalcul suppose une **référence pour 100 g**, celle d'une fiche d'aliment. La saisie à la main n'en a aucune : il n'existe pas de fiche derrière une ligne tapée au clavier.

**Choix.** `DraftLine` porte les six valeurs **telles qu'elles ont été saisies**, pour la quantité indiquée. Changer la quantité ne les recalcule pas.

**Raison.** La seule référence disponible serait celle que l'utilisateur vient de taper, et s'en servir pour réécrire ses propres chiffres reviendrait à inventer une règle qu'il n'a pas demandée : « 200 g, 300 kcal » deviendrait « 400 g, 600 kcal » alors qu'il corrigeait peut-être une erreur de pesée. Le recalcul arrive avec ce qui le justifie — une fiche d'aliment et ses valeurs pour 100 g, en tranche 3.

**Deux unités seulement, g et ml.** Les portions nommées — « 1 tranche », « 1 verre » — appartiennent à une fiche. Un millilitre vaut un gramme, ce qui est la densité par défaut de [04](04-sources-de-donnees.md) et la seule dont on dispose ; la conversion est isolée dans `QuantityUnit` pour qu'il n'y ait qu'un endroit à corriger.

**Un champ vide vaut inconnu, jamais zéro.** C'est la règle du projet, appliquée là où elle est le plus facile à trahir : il aurait suffi de lire un champ vide comme `0` pour éviter tout traitement du cas nul, et le journal aurait porté des zéros que personne n'a saisis. Seule l'énergie est obligatoire — une fiche sans énergie n'est pas exploitable. Trois tests couvrent le point.

---

## D43 — Les cas d'usage d'écriture parlent de plats · ✓ validée

**Contexte.** [06](06-architecture.md#cas-dusage) et [12](12-plan-de-developpement.md) nomment `LogFoodEntry`, `UpdateFoodEntry`, `DeleteFoodEntry`. Ces noms datent d'avant [D31](#d31--un-plat-pas-un-repas-nommé--validée), qui a fait du **plat** l'unité de saisie.

**Choix.** `LogDish`, `UpdateDish`, `DeleteEntry`, `RestoreDish`, `GetDishDraft`, `CreateDraft`. On enregistre un plat, on modifie un plat ; on supprime en revanche une **ligne**, parce que c'est bien une ligne qu'on balaie dans le journal.

**Pourquoi ça compte plus qu'un nom.** `LogFoodEntry` laisserait croire qu'une ligne entre seule dans le journal. Elle ne le peut pas : elle appartient à un plat, qui porte la source et l'heure. Le nom qui ment ici est celui par lequel on finirait par écrire une ligne orpheline.

**Une conséquence, tirée du même raisonnement.** `DeleteEntry` supprime le plat quand il perd sa dernière ligne. Un plat vide s'afficherait avec son heure et sa pastille de source, à zéro calorie, indiscernable d'une saisie réelle qui n'aurait rien apporté.

---

## D44 — La lueur de l'hexagone est un contour, pas une silhouette · ✓ validée

**Contexte.** Constaté sur appareil : le néon de l'hexagone ne se voyait que sur l'arête extérieure des quartiers, avec un bord franc, et se faisait rogner quand un quartier remplissait la zone.

**La cause.** La lueur était un **second triangle un peu plus grand**, en teinte `glow`, posé derrière le quartier. Trois défauts en découlaient, et ils étaient tous inévitables avec cette forme :

- elle ne dépassait visiblement que d'un côté — les deux arêtes latérales sont mitoyennes, et le remplissage du quartier voisin recouvrait ce qui dépassait de son côté ;
- un triangle plein s'arrête où il s'arrête : le bord était net, et **une lueur qui s'arrête net n'est pas une lueur** ;
- son rayon valait 106 % de celui du quartier, or le quartier le plus rempli atteint déjà le bord de la zone. Les 6 % de trop sortaient du dessin et se faisaient couper au ras.

**Choix.** La lueur est un **tracé en contour sur les trois arêtes**, en trois passes de plus en plus larges et de moins en moins opaques — la technique déjà employée par `NeonButton`. Le tracé étant centré sur le chemin, chaque couche déborde de part et d'autre, et les six lueurs se rejoignent au centre où les six pointes se touchent.

Deux conséquences de forme, sans lesquelles le remède serait incomplet :

- **Deux passes de dessin.** Les six quartiers d'abord, les six lueurs ensuite. Dessinée quartier par quartier, chaque lueur latérale se ferait recouvrir par le remplissage du suivant — c'est-à-dire exactement le défaut d'origine, sous une autre forme.
- **Une réserve dans le rayon.** La zone déduit désormais la lueur, un intervalle et la lettre. Rien ne peut plus être rogné par le bord.

**Écarté.** *Un vrai flou* (`BlurMaskFilter`) : ce serait le rendu juste, mais il n'est pas accéléré matériellement et imposerait un rendu logiciel à chaque image d'une figure animée à 400 ms. Trois couches suffisent à ce que l'œil ne distingue plus les paliers.

**Conséquences.** Environ cinquante tracés par image contre six, tous sur des chemins triangulaires simples. Le dégradé des totaux minorés s'applique désormais à la lueur comme au remplissage, sans quoi une arête volontairement floue se serait retrouvée soulignée d'un trait de néon parfaitement net.

---

## D45 — Un champ de saisie tient son texte lui-même · ✓ validée

**Contexte.** Constaté sur appareil : taper vite dans un champ de l'écran de validation mélangeait les lettres. « Bolognaise » donnait « Boognaseil » — le curseur reculait de deux caractères en cours de frappe.

**La cause.** La forme habituelle, `value = état.texte` et `onValueChange = { viewModel.change(it) }`, suppose que l'état revienne avant la frappe suivante. Il ne revenait pas : entre la frappe et le nouvel état, il y avait un `MutableStateFlow`, un `combine`, un `flowOn(default)` et une recomposition. Une frappe arrivée avant la fin du trajet trouvait un champ réaffiché avec un texte d'il y a deux caractères, et la position du curseur repartait avec lui.

**Choix.** Le texte affiché vit dans le champ, en `TextFieldValue` local. Chaque frappe s'y applique immédiatement ; le `ViewModel` est prévenu ensuite et ne renvoie rien. **Il n'y a plus qu'un seul écrivain, donc plus de course.**

Le `flowOn(dispatchers.default)` disparaît aussi de cet écran. Ce qu'il produisait à chaque frappe tient en une conversion de quelques lignes et deux additions : le passer sur un autre dispatcher n'économisait rien et coûtait une image de latence.

**Ce que ça ne casse pas.** La valeur initiale n'est lue qu'à la première composition, et c'est suffisant : une ligne est identifiée par son `DraftLineId` dans la liste, donc rouvrir un plat ou replier les valeurs reconstruit le champ avec le bon texte. Rien d'autre ne réécrit ce que l'utilisateur tape.

**Une règle qui suit.** Le champ **refuse** une frappe non numérique au lieu de l'accepter puis de la nettoyer. Nettoyer obligerait à réécrire le texte affiché, donc à repositionner le curseur — le défaut qu'on vient de corriger. Le filtrage disparaît en conséquence de `LineEdit` : deux règles pour la même chose divergent le jour où l'une accepte ce que l'autre supprime.

**Portée.** Cette décision vaut pour tout champ de saisie du projet, pas seulement pour celui-ci. Elle contredit en apparence la forme de [06](06-architecture.md#présentation) — « un `StateFlow<UiState>` unique » — mais seulement en apparence : l'état d'écran reste unique et reste un flux ; c'est le **texte en cours de frappe** qui n'y transite plus, parce qu'il n'a pas le temps.

---

## D46 — Une action destructrice ne s'atteint jamais par le seul balayage · ✓ validée

**Contexte.** L'écran de validation ne proposait le retrait d'une ligne que par balayage.

**Choix.** Une corbeille visible à droite du nom, **en plus** du balayage.

**Raison.** Un geste sans représentation visible est introuvable pour qui ne le connaît pas, hors d'atteinte au lecteur d'écran, et difficile pour une main qui tient mal le téléphone. Le balayage reste ce qu'il doit être : le raccourci de celui qui le connaît.

**Ce qui reste ouvert.** L'accueil n'a, lui, que le balayage sur ses lignes de journal, et la même critique s'y applique. La différence est qu'il y existe déjà un appui long prévu par [02](02-parcours-et-ecrans.md#liste-des-plats) — dupliquer, déplacer, mettre en favori — et que la suppression y a sa place. À traiter avec ce menu, pas avant : deux chemins ajoutés séparément en feraient trois.

---

## D47 — Les six macros brillent · ⊘ en partie remplacée par D48

**Contexte.** Constaté sur appareil : trois macros allumées et trois éteintes ne se lisent pas comme une information mais comme un défaut d'affichage. L'utilisateur ne voit pas une règle nutritionnelle, il voit un rendu qui marche à moitié.

**Ce que disait la règle précédente.** [D27](#d27--objectif-ou-limite--la-nature-appartient-à-la-macro--en-partie-remplacée-par-d47) : une limite reste sourde sous son seuil et ne s'allume qu'au dépassement. « Ne pas allumer une limite, c'est déjà réussir. » L'idée est juste ; ce qui ne tient pas est de la porter par l'**absence** d'un effet. Une absence ne se distingue pas d'une panne.

**Choix.** Les six macros prennent leur teinte vive et leur lueur, à tout niveau, dans l'hexagone comme dans les barres. La distinction objectif / limite reste portée par `Macro` dans le domaine — c'est le cœur de D27 et il ne bouge pas — mais elle s'exprime désormais par ce qui est **écrit**, pas par ce qui est éteint : le suffixe `max` sur la valeur, le repère de seuil, l'échelle élargie à 125 %, et la phrase annoncée par TalkBack.

**Ce qui est perdu, et qui est réel.** Une journée bien tenue ne se reconnaît plus d'un coup d'œil à ses trois quartiers sourds. Il faut lire les valeurs. C'est le prix d'une figure qui ne ressemble plus à un rendu incomplet, et il est assumé.

**Trois corrections qui accompagnent.**

- **Plafond ramené de 200 % à 150 %.** À 200 %, la cible tombait à la moitié de sa taille et les six lettres se retrouvaient loin d'une figure devenue petite. Un dépassement de moitié se voit largement.
- **Le dégradé d'un total minoré devient linéaire**, le long de l'axe du quartier. Radial, il suivait un cercle : les deux sommets — à `R` du centre — disparaissaient entièrement pendant que le milieu de l'arête — à `√3/2 · R` — restait presque opaque. Le quartier paraissait rongé par les coins au lieu d'être estompé sur son bord.
- **La lueur cesse de grandir sous 12 % du rayon.** Sa largeur est fixe, celle du quartier ne l'est pas : sur un quartier presque vide, elle était plus grande que lui et tachait le centre.

**Reste ouvert.** Sur les barres, le repère de seuil et l'échelle à 125 % subsistent — ce ne sont pas des effets de néon et ils portent la seule distinction visuelle restante. Les retirer aussi rendrait une limite strictement indiscernable d'une cible hors du texte.

> **Ce dernier point est remplacé par [D48](#d48--la-barre-pleine-vaut-lobjectif--validée).** L'échelle permanente à 125 % cède, et avec elle la dernière distinction visuelle entre une cible et une limite. Tout le reste de cette entrée — les six macros brillent, le plafond à 150 %, le dégradé linéaire, la lueur qui cesse de grandir — tient toujours.

---

## D48 — La barre pleine vaut l'objectif · ✓ validée

**Contexte.** [D47](#d47--les-six-macros-brillent--en-partie-remplacée-par-d48) avait laissé aux barres deux signaux visuels : une échelle élargie à 125 % sur les limites, et un repère planté au seuil. Ces deux-là étaient permanents — donc présents à zéro, alors qu'ils ne parlent que du dépassement.

**Ce que ça produisait.** Une barre dont le remplissage ne se lisait pas seul. À 100 %, la jauge de sucres était aux quatre cinquièmes, et il fallait avoir compris le repère pour savoir que c'était le plafond et non 80 % de celui-ci. Le signal censé lever un doute en créait un.

**Choix.** La barre pleine vaut l'objectif, pour les six macros. Au-delà, **l'échelle suit la valeur** : le remplissage recule à mesure que la quantité monte, et un repère apparaît là où l'objectif se situe désormais. En dessous, il n'y a rien à interpréter.

**C'est le mécanisme de l'hexagone**, et il emprunte son plafond de 150 % pour la même raison — une saisie erronée à 2 000 % tasserait tout contre l'origine, précisément au moment où il faut lire la barre pour corriger ([D33](#d33--un-hexagone-en-tête-daccueil-et-un-seul-ordre-angulaire--validée)). Les deux composants disent désormais le dépassement de la même façon, ce qui est une raison de plus : ils sont l'un au-dessus de l'autre sur l'accueil.

**Ce qui est perdu, et qui est réel.** Plus rien de visuel ne distingue une limite d'une cible. C'est exactement ce que D47 refusait de céder. Restent le suffixe `max` sur la valeur et la phrase de TalkBack — deux canaux textuels, dont un seul est visible à l'œil. Un dépassement de sucres se voit désormais parce que la barre a rétréci, pas parce que c'était une limite.

**La lueur perd sa dernière condition.** Elle était atténuée proportionnellement au remplissage ; elle est désormais pleine à tout niveau. C'était le même défaut que celui traité par D47, sous une forme continue plutôt que binaire : une barre peu remplie paraissait mal rendue plutôt que basse. La transparence en thème clair reste, mais c'est une propriété de la palette et non une condition de la barre.

**Deux corrections d'atteinte, prises au même moment.**

- **Le plat entier est la cible tactile de l'accueil.** Seules les lignes d'aliment l'étaient ; l'heure, la pastille, le total et les apports — la moitié de la surface — ne répondaient pas, sans que rien ne dise pourquoi. Le plat est l'unité de saisie ([D31](#d31--un-plat-pas-un-repas-nommé--validée)), donc l'unité de correction. Conséquence assumée : une cible tactile fusionne les nœuds d'accessibilité qu'elle contient, et un plat devient un seul arrêt de TalkBack au lieu de *n* + 4. La phrase de chaque ligne est ce qui rend cette annonce lisible, et c'est pour ça qu'elle reste.
- **Enregistrer et Annuler flottent au-dessus de la liste.** En pied de défilement, ils s'éloignaient à mesure que le plat grossissait : à cinq lignes dépliées, enregistrer demandait de faire défiler un écran entier. La réserve laissée sous la liste est **mesurée** et non déclarée — une hauteur écrite en dur ferait passer le dernier champ sous les boutons à 200 % de police. L'explication de ce qui manque quitte l'affichage permanent pour redevenir la **réponse** du bouton indisponible à un appui, ce que [D28](#d28--un-bouton-indisponible-réagit-quand-même--validée) demandait déjà : épinglée, elle occuperait quatre lignes à chaque saisie neuve pour dire ce que les champs vides disent déjà.

---

## D49 — La recherche normalise à l'import, pas au tokenizer · ✓ validée

**Contexte.** [04](04-sources-de-donnees.md) et [07](07-modele-de-donnees.md) demandaient une table FTS5 avec `unicode61 remove_diacritics 2`, et c'est ce réglage qui devait faire que « creme brulee » trouve « crème brûlée ». Vérifié avant d'écrire la première ligne : il ne tient pas sous `minSdk 26`, et pour deux raisons indépendantes.

**Ce qui ne tient pas.** FTS5 n'est compilé dans le SQLite embarqué d'**aucune** version d'Android — c'est précisément pourquoi Room n'expose que `@Fts3` et `@Fts4`, et pourquoi il existe des bibliothèques dont le seul objet est d'embarquer un SQLite qui l'a. Et `remove_diacritics 2` demande SQLite 3.27, donc l'API 29 : les API 26 à 28 échoueraient même si FTS5 était là. Un défaut de ce genre ne se voit pas ici : il se voit chez l'utilisateur, sous la forme d'une recherche qui ne rend jamais rien.

**Choix.** La colonne indexée est un nom **déjà normalisé au build** — décomposition Unicode, marques diacritiques retirées, ligatures défaites, minuscules, ponctuation devenue coupure de mot. L'index est une table FTS4 sans contenu, tokenizer `simple`. La même fonction est appliquée à la saisie, et c'est la seule règle qui compte : un nom indexé sans elle, ou une saisie comparée sans elle, ne se rencontrent jamais.

**Écarté.** *Embarquer SQLite* (requery, `androidx.sqlite` bundled) : garderait la lettre de la spécification, au prix de 4 à 5 Mo d'APK, d'une dépendance native, et d'une fabrique d'ouverture à rebrancher — ce qui toucherait aussi `hexaphore.db`. *Remonter `minSdk` à 29* : ne réglerait que la moitié du problème, celle qui n'était pas la plus grave.

**Ce que ça gagne, en plus de fonctionner.** La normalisation est faite une fois, au build, par la JVM, dont la couverture Unicode dépasse largement le latin-1 auquel `remove_diacritics 2` se limite. Elle se teste en JVM pure. Et `œ` — l'un des trois exemples de [D23](#d23--recherche-dès-le-2ᵉ-caractère-après-une-pause-de-frappe--validée) — est traité, ce qu'aucun réglage de tokenizer n'aurait fait : `NFD` sépare une lettre de son accent, mais `œ` n'est pas un `o` accenté.

**Ce que ça coûte.** `bm25()` est une fonction de FTS5 : le classement est calculé côté Kotlin. Le coût est faible parce que [04](04-sources-de-donnees.md) exigeait déjà un second critère par-dessus BM25 — remontée des aliments courts et déjà consommés — et que c'est lui qui départage vraiment 3 484 libellés courts. `tokenize=simple` plutôt qu'`unicode61` pour la même raison que le reste : `name_search` est de l'ASCII minuscule séparé par des espaces, les deux tokenizers y font le même découpage, et `simple` est le seul dont la présence ne se discute pas.

**Trois autres points tranchés dans la même passe.**

- **Une écriture de teneur inconnue arrête l'import.** Le parseur a trois issues et non deux : la valeur, l'inconnu déclaré, et ce qu'il ne sait pas lire. Ranger la troisième avec l'inconnu effacerait une colonne entière en silence le jour où l'ANSES change de convention ; la ranger avec zéro en inventerait une. Les deux replis sont aussi graves, et aucun ne se voit avant des mois de journal faussé.
- **Le seuil de `<` est quelconque.** [04](04-sources-de-donnees.md) ne citait que `< 0,5`. Dépouillement du fichier réel : 250 seuils distincts, de `< 0,0001` à `< 700`, pour 16 000 valeurs. La règle est `< n → n / 2`, et l'exemple n'était qu'un exemple.
- **Le code de constituant fait foi, l'intitulé le vérifie.** Désigner une colonne par son libellé accentué ferait dépendre l'import d'une chaîne qui bouge ; ne se fier qu'au code laisserait une renumérotation remplir les lipides avec autre chose. Les deux sont déclarés, et l'import échoue si l'un dément l'autre. C'est la seule vérification qui protège d'une erreur qu'aucun test ne verrait : la base se génère, l'application se lance, et les chiffres sont faux.

**Un constat qui a changé une intention.** 143 aliments sur 3 484 n'ont pas d'énergie déterminée. L'intention était de les écarter — [D42](#d42--une-ligne-de-brouillon-porte-des-valeurs-absolues--par-défaut) dit qu'une fiche sans énergie n'est pas exploitable. Regardés de près, ce sont la feta, les câpres, la canneberge, le pruneau cuit, l'estragon frais. Les écarter aurait retiré des aliments courants du catalogue pour appliquer une règle écrite à propos d'une ligne tapée à la main. Ils restent, avec leur trou visible : c'est exactement le comportement que le projet demande partout ailleurs.

---

## D50 — Ce que la tranche 3 ne construit pas · ✓ validée

Listés ici pour cesser d'être des oublis, comme [D21](#d21--ce-que-litération-0-ne-construit-pas--par-défaut) et [D40](#d40--ce-que-la-tranche-2-ne-construit-pas--validée) l'ont fait avant.

| Absent | Raison | Quand |
|---|---|---|
| Les **plats** favoris (`favorite_dish`, `favorite_component`) | [02](02-parcours-et-ecrans.md#modale--recherche) dit « favoris : aliments **et** plats ». Les aliments favoris existent ; les plats demandent deux tables, une action « enregistrer comme favori » sur l'écran de validation, et un rejeu qui reconstruit un brouillon à partir de fiches vivantes. C'est une capacité, pas une case à cocher. | Avec la réutilisation d'un plat entier |
| `food_serving`, les portions nommées d'un **aliment personnel** | Les portions de CIQUAL se lisent dans `ciqual_serving` par le code source, sans copie. Une fiche personnelle a `default_serving_g`, qui couvre le cas courant. Une table que rien ne remplirait serait exactement ce que [D34](#d34--la-table-food-attend-la-tranche-qui-la-remplit--par-défaut) refusait. | Quand un aliment personnel aura besoin de plusieurs portions |
| Les colonnes `density`, `is_liquid`, `user_edited_fields`, `fetched_at` de `food` | Même raison, appliquée colonne par colonne. La densité arrive avec le résolveur (tranche 6), les trois autres avec le cache Open Food Facts (tranche 5), qui est ce qui les remplit. La règle du projet préfère une colonne nullable ajoutée plus tard à une colonne vide ajoutée trop tôt. | Tranches 5 et 6 |
| La suggestion « Chercher dans Open Food Facts » en dernière ligne de résultats | Elle suppose un client réseau, qui est le contenu de la tranche 5. Une ligne qui n'ouvre rien n'est pas une avance. | Tranche 5 |
| Un brouillon **multi-lignes** transmis d'un écran à l'autre | La recherche produit une ligne, et une route la porte par son identifiant. La photo en produira cinq, et une route ne les portera pas : il faudra un brouillon en attente, partagé. Ajouter un argument de route par mode serait la première marche vers l'écran à quatre branches que le projet refuse. | Tranche 6 |

**Ce que ça ne coûte pas.** La capacité annoncée par la tranche — « je cherche un aliment » — est entière : on cherche, on trouve hors-ligne, on reprend un récent, on épingle un favori, on crée ce qui manque, et la quantité recalcule les valeurs.

**Une décision prise en route, qui mérite d'être écrite.** Un aliment de la table de l'ANSES entre au catalogue au moment où le plat qui le cite est enregistré, et pas au moment où on le choisit dans la liste. C'est `LogDish` qui appelle `FoodUsage.remember`, **avant** l'écriture du plat — une entrée qui désigne une fiche absente n'existe pas, la base la refuse, et l'ordre inverse aurait paru plus prudent sans jamais fonctionner. Marquer au tap aurait été plus simple et ferait remonter dans « Récents » un aliment qu'on a regardé puis abandonné : cette liste dit ce qu'on mange.

---

## D51 — Une seule porte, et la quantité qui recalcule · ✓ validée

**Contexte.** Constaté sur appareil : choisir un aliment dans la recherche ouvrait un écran de saisie **vide**. Et changer la quantité d'une ligne ne changeait rien à ses valeurs.

**La cause du premier défaut, qui était une faute de conception.** Un résultat de la table de l'ANSES recevait un identifiant **provisoire**, régénéré à chaque recherche : il n'entrait dans `food` qu'à l'enregistrement du plat. La route ne transportait que cet identifiant, et l'écran de validation le cherchait dans une table où il n'existait pas. Il ne trouvait rien et retombait sur un brouillon vierge. Cela ne marchait que pour un récent, un favori ou un aliment personnel — les fiches déjà écrites — c'est-à-dire pour les cas qu'un test en mémoire couvre et qu'un appareil ne montre qu'une fois le catalogue rempli.

**Choix.** La fiche est **versée au catalogue au moment du choix**, et c'est son identifiant définitif qui voyage. Le port `CustomFoodStore` devient `FoodStore` et gagne `place`, qui écrit si la fiche est absente et rend celle qui est là — jamais l'inverse, sans quoi choisir un aliment remettrait ses compteurs d'usage à zéro. Le rapprochement se fait par `(source, source_ref)` et non par l'identifiant, précisément parce que celui-ci était le provisoire.

**Écarté.** *Un identifiant déterministe* du genre `ciqual:13039` : stable, mais il abandonne la convention UUIDv4 de [07](07-modele-de-donnees.md) et n'est pas réversible, donc la recherche par identifiant aurait quand même eu besoin d'un repli. *Faire transiter la fiche entière par la route* : une route est sérialisée dans l'état de navigation, et y mettre un objet du domaine y ferait entrer Android.

**Ce que ça coûte.** Une fiche regardée puis abandonnée reste au catalogue, avec `last_used_at` nul — donc absente de « Récents », et indiscernable de la ligne de l'ANSES qu'elle est. Le coût est une ligne par aliment réellement ouvert, et il achète un identifiant qui désigne toujours quelque chose.

**Le second défaut, et sa règle.** Les valeurs d'une ligne étaient calculées une fois, à sa naissance. Une ligne porte désormais sa **référence pour 100 g**, et changer la quantité ou l'unité recalcule. La référence est capturée à la naissance de la ligne et **reconstruite depuis les valeurs figées** quand on rouvre un plat : la règle de trois ne relit jamais la fiche, qui a pu être corrigée ou supprimée depuis ([D05](#d05--les-entrées-de-journal-figent-leurs-valeurs--validée)). Les valeurs affichées à l'ouverture restent exactement celles enregistrées, puisque la quantité n'a pas bougé.

**Une valeur corrigée à la main ne bouge plus**, ce que [02](02-parcours-et-ecrans.md#écran-de-validation-dentrée) demandait depuis la conception et que rien ne tenait. Vider un champ compte comme une correction : c'est une affirmation — « je ne sais pas » — et la quantité n'a pas à la contredire au gramme suivant. Le marqueur est posé par macro et par ligne.

**Le point technique qui rend le recalcul visible.** Un champ de saisie tient son propre texte et ne relit sa valeur initiale qu'à la première composition ([D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même--validée)). Sans un signal supplémentaire, le recalcul aurait mis à jour le brouillon **sans que l'écran bouge**. Chaque ligne porte donc un compteur de révisions, incrémenté par le recalcul et par lui seul : il sert de clé de composition aux six champs. Une frappe ne l'incrémente pas, donc le curseur ne bouge pas.

**Une seule porte pour ajouter.** L'accueil n'a plus qu'un bouton, et il ouvre la recherche. La saisie manuelle y est une action permanente plutôt qu'une porte à côté, et **elle crée une fiche** : un aliment tapé à la main se cherche, se reprend et se recalcule comme les autres. « Ajouter un aliment » depuis l'écran de saisie rouvre la même recherche, dont le choix revient au brouillon en cours par le canal que la navigation prévoit pour un résultat.

**Ce que la saisie manuelle coûte sous cette forme.** Elle se saisit **pour 100 g** et non pour la quantité mangée : c'est le prix de la fiche, et c'est ce qui rend la ligne recalculable. Pour « reste d'hier, 300 kcal », il faut donc penser en pour-cent-grammes une fois — et une seule, puisque la fiche revient ensuite.

**Ce que ça rend nécessaire, et qui est fait.** Le catalogue accumule ce qu'on saisit. Une fiche créée par l'utilisateur se **voit** dans les listes et porte une **corbeille** ; une ligne de la table de l'ANSES n'en a pas, c'est une référence publiée. La suppression demande confirmation, et la phrase change selon l'usage : une fiche citée par douze entrées annonce que celles-ci sont conservées telles quelles, avec leurs valeurs figées.

---

## D52 — Deux `SavedStateHandle`, une seule saisie manuelle, des grammes entiers · ✓ validée

**Contexte.** Constaté sur appareil : « Ajouter un aliment » depuis une saisie en cours ne faisait **rien**. On revenait à l'écran avec la seule ligne de départ, et il devenait impossible de composer un plat de plus d'un aliment.

**La cause, et c'est un piège d'API.** Le `SavedStateHandle` qu'un `ViewModel` reçoit et celui que porte un `NavBackStackEntry` sont **deux objets différents**. Tous deux sont construits par `createSavedStateHandle` depuis le même registre, mais sous deux clés distinctes : ils ne partagent aucun état. La recherche écrivait dans celui de l'entrée de pile, le `ViewModel` observait le sien, et personne ne remplissait jamais la clé qu'il écoutait.

**Ce qui rendait l'erreur crédible.** Le **premier** aliment arrivait, lui. Les arguments de route passent par les deux handles — ils sont dans le `Bundle` d'arguments par défaut — donc le chemin « accueil → recherche → saisie » fonctionnait, et seul l'ajout d'une deuxième ligne échouait. Un défaut qui marche à moitié est plus difficile à voir qu'un défaut qui ne marche pas.

**Choix.** Le résultat se lit sur le `NavBackStackEntry`, dans la composable de la destination, et l'écran le passe au `ViewModel`. C'est le chemin que la navigation Compose prévoit ; le lire dans le `ViewModel` était une commodité qui n'existe pas.

**Ce que le test manquait.** Il écrivait dans le `SavedStateHandle` qu'il construisait lui-même, celui du `ViewModel` — donc il éprouvait un chemin qui n'existe nulle part. Il passait pendant que l'écran ne faisait rien. Les tests appellent désormais la méthode que la composable appelle, et trois cas de plus couvrent ce que la ligne ajoutée doit porter.

**La recherche n'est plus une source.** `EntrySource.SEARCH` disparaît. Elle se confondait avec `MANUAL` : un même plat mêle couramment un aliment trouvé dans la table et un autre saisi à la main, et un plat porte **une** source ([D32](#d32--la-source-appartient-au-plat-et-ne-change-jamais--validée)) — distinguer les deux revenait à choisir laquelle mentir. Le typage reste, parce que ce qu'il devra dire un jour est autre chose : ce qui a été **proposé** par un modèle mérite un regard que ne mérite pas ce qu'on a composé soi-même, et `proposed` le porte déjà. Une base antérieure porte encore `SEARCH` ; elle se relit en `MANUAL`, ce que la lecture prudente du mapper faisait déjà.

**Les six valeurs sont des grammes entiers.** Personne ne compte les demi-grammes de lipides, et une décimale affichée est une précision promise que la source ne tient pas : CIQUAL donne 0,25 g de protéines pour une pomme parce que la mesure est sous le seuil de quantification, pas parce qu'elle vaut un quart de gramme. Le séparateur décimal quitte donc le clavier **et** le filtre de ces champs — l'accepter pour arrondir ensuite obligerait à réécrire le texte affiché, donc à repositionner le curseur ([D45](#d45--un-champ-de-saisie-tient-son-texte-lui-même--validée)).

**L'arrondi a lieu à l'aller, pas seulement à l'écran.** Ce qui est affiché est ce qui sera enregistré. Arrondir à la seule présentation ferait diverger le chiffre lu de celui écrit dans le journal — la définition d'un écran qui ment. La référence pour 100 g, elle, garde sa précision : c'est elle qui recalcule, et l'arrondir la ferait dériver à chaque changement de quantité. La quantité garde aussi ses décimales : 12,5 g d'huile est une pesée, pas une approximation.

**Le rognage du plat disparaît de l'accueil.** [D48](#d48--la-barre-pleine-vaut-lobjectif--validée) avait ajouté des coins arrondis pour borner l'ondulation du tap. Ils coupaient la pastille de source et le total de calories, qui sont aux deux extrémités de la première ligne. L'ondulation déborde donc en rectangle, et c'est le prix à payer pour que rien ne soit tronqué.

---

## D53 — La recherche est un flux, et le faux est tenu par un contrat · ✓ validée

**Contexte.** Constaté sur appareil : dans la recherche, épingler un aliment ou supprimer une fiche personnelle **ne se voyait pas**. Il fallait relancer la recherche. Les raccourcis — récents et favoris — se rafraîchissaient bien, eux.

**La cause, et l'asymétrie qui la rendait visible.** `SearchUiState.Results` venait d'un appel unique à `FoodSearch.search`, une `suspend fun`. Les raccourcis venaient de `Flow`. Écrire dans le catalogue n'invalidait donc rien du côté des résultats : une lecture unique rend un instantané, et **un instantané ne peut pas se démentir**.

**Choix.** `FoodSearch.search` rend un `Flow<List<Food>>`. Room invalide sur écriture, exactement comme pour `observeRecent` — le mécanisme existait déjà à trois lignes de là.

**Écarté.** *Un déclencheur de relecture* poussé après chaque écriture : c'est un `Flow` reconstruit à la main, avec une invalidation à ne pas oublier à chaque nouvelle écriture. Room la connaît déjà et ne l'oublie jamais. *Recomposer la liste côté écran* à partir des favoris observés : ça n'aurait couvert que l'étoile, pas la suppression ni le versement au catalogue, et ça aurait mis la fusion des provenances dans le `ViewModel`.

**Le flux vient du catalogue local, et lui seul.** La table de l'ANSES est livrée en lecture seule et ne change jamais ; elle est relue à chaque invalidation, ce qui coûte deux requêtes sur des libellés courts. C'est ce qui garde le dédoublonnage juste au moment précis où une fiche vient d'être copiée — sans quoi elle apparaîtrait deux fois pendant une image.

**Un second défaut, de la même famille, que la correction a mis à nu.** Épingler un aliment de la table de l'ANSES **non encore copié** n'allumait aucune étoile, et pour une raison indépendante du flux : `setFavorite` recevait l'identifiant **provisoire** ([D51](#d51--une-seule-porte-et-la-quantité-qui-recalcule--validée)) et ne mettait à jour aucune ligne. L'écran verse donc la fiche au catalogue avant d'épingler, comme il le fait déjà pour la choisir. L'état épinglé est lu sur la fiche **rendue** par le catalogue, jamais sur celle qu'on affiche.

### Le faux était plus indulgent que le vrai, et c'est ça qu'il fallait corriger

Trois défauts de suite avaient la même forme, et **à chaque fois le test passait**. La cause n'est pas la paresse du test : c'est que `InMemoryFoodCatalog` ne rendait que des fiches **déjà écrites**, alors que `RoomFoodCatalog` en fabrique qui n'y sont pas encore. Le faux ne modélisait pas la moitié du monde, donc un test écrit contre lui éprouvait un chemin que l'application n'emprunte jamais.

**Choix, en deux parties indissociables.**

- **Le faux gagne une table de référence.** `InMemoryFoodCatalog(initial, reference)` : la seconde liste joue la table de l'ANSES — trouvable, jamais écrite, avec un identifiant provisoire **régénéré à chaque recherche**. Son `place` rapproche par `(source, source_ref)` et non par l'identifiant, comme le vrai. Sans cette moitié-là, le test qui attrape le défaut de l'étoile ne pouvait même pas s'écrire.
- **Un jeu de tests de contrat, écrit une fois et exécuté deux fois.** `FoodCatalogContract` couvre **six des sept ports** du projet — c'est la même paire de classes qui les porte tous — et `RoomFoodCatalogTest` comme `InMemoryFoodCatalogTest` en héritent. Une propriété que le faux s'autorise à ne pas tenir devient une ligne rouge à côté d'une verte, et non une découverte sur l'appareil.

**Il vit dans `:data:food`, pas dans `:core:testing`.** Les deux implémentations sont ainsi compilées et exécutées **côte à côte**, sous la même commande et dans le même rapport. En contrepartie, JUnit 4 et Robolectric entrent dans un second module — le contrat a besoin de Room, donc d'Android, et Robolectric est un lanceur JUnit 4. C'est l'extension de [D35](#d35--le-test-de-migration-tourne-sur-la-jvm-pas-sur-un-appareil--validée) et non sa contradiction : JUnit 4 reste cantonné aux modules qui ne peuvent pas s'en passer, et le moteur vintage les rassemble sous `./gradlew check`.

**La table de l'ANSES n'est pas garnissable**, puisqu'elle est livrée dans l'APK. Les fiches de référence du contrat sont donc de **vraies lignes** — codes 13039, 13037, 39213 — et la sous-classe Room **vérifie que le code et l'intitulé concordent** avant de jouer quoi que ce soit. Sans ce contrôle, une fixture périmée rendrait zéro résultat et la moitié des cas passeraient en ne mesurant rien : exactement le vert qui a déjà coûté deux corrections.

**Les deux corrections ont été éprouvées en les défaisant.** Remettre la recherche en lecture unique fait tomber quatre tests, et **du seul côté Room** — le faux, lui, passait toujours, ce qui est la démonstration littérale du problème. Retirer le versement au catalogue avant l'épinglage fait tomber le test du `ViewModel`.

**Ce que ça a révélé, et qui était un vrai défaut latent.** Un test de la tranche 3 affirmait que choisir un aliment rendait l'identifiant **provisoire** qu'on lui présentait. Il n'éprouvait que l'indulgence du faux : sur Room, choisir un aliment dont le code CIQUAL est déjà au catalogue rend l'identifiant **existant**, et c'est le comportement correct — l'inverse recopierait la fiche et remettrait ses compteurs d'usage à zéro. Le test disait donc le contraire de la règle qu'il croyait garder.

**Conséquences.** `:core:testing` gagne `TestDispatchers`, qui était recopié en privé dans les modules de test. Le port `FoodSearch` n'a plus de `suspend`, ce qui touche son unique appelant. Il reste **un** port à deux implémentations non couvert par un contrat, `DiaryRepository` ; la tranche 4 en ajoutera, et ils rejoindront le même dispositif.

---

## D54 — Un bandeau de rayons, et deux familles qui ne se combinent pas pareil · ✓ validée

**Contexte.** La recherche ne se parcourt pas : il faut savoir quoi taper. Un bandeau de pastilles sous la barre donne une entrée pour ceux qui ne cherchent pas un aliment précis mais **une sorte** d'aliment.

**Choix.** Huit rayons — Fruits, Légumes, Féculents, Viandes et poissons, Produits laitiers, Boissons, Desserts, Snacks — plus deux qualités, « Favori » et « Mon aliment ».

**Huit, et pas quarante-cinq.** La nomenclature de l'ANSES compte 45 sous-groupes ; c'est une classification de laboratoire, pas un bandeau qu'on parcourt au pouce. La correspondance est une **table maison versionnée**, `CiqualCategories` dans `:tooling:ciqual-import`, appliquée à l'import.

### La catégorie descend jusqu'au domaine

`Food.category` est un `FoodCategory?` du domaine, et le filtre est `FoodFilter`, une classe de `:domain` avec sa méthode `matches`. Un tag qui n'aurait été qu'une clause `WHERE` dans l'adaptateur ne s'éprouverait que sur un appareil, alors que **c'est une règle de ce que l'utilisateur voit**. Le SQL n'en est que l'accélération — sur 3 484 lignes, filtrer en Kotlin obligerait à toutes les lire pour en rendre trente — et le contrat de `FoodSearch` ([D53](#d53--la-recherche-est-un-flux-et-le-faux-est-tenu-par-un-contrat--validée)) vérifie que les deux disent la même chose.

**Deux familles, deux combinaisons.** Les rayons entre eux en **OU** : aucun aliment n'étant à la fois un fruit et un légume, l'intersection ne rendrait jamais rien. Les qualités en **ET** par-dessus : « Favori + Fruits » montre les fruits épinglés.

**Rien dans un texte ne dit qu'il y a deux familles**, donc trois canaux le disent, et aucun ne travaille seul : la **position** — les qualités ouvrent le bandeau —, un **trait** qui sépare les deux blocs, et la **forme** — une qualité est une pastille ronde à icône, un rayon un rectangle de texte. Un quatrième existe pour ceux qui ne voient rien : TalkBack annonce « Favori, qualité » contre « Fruits, rayon ». La règle de [08](08-design-system.md#daltonisme) est la même que partout — la couleur de sélection ne porte jamais seule une information.

### Le rayon n'est pas stocké dans `food`

Une fiche copiée au catalogue ne porte pas sa catégorie : elle est relue dans `ciqual.db` par `(source, source_ref)`, en un seul lot, exactement comme les portions le sont déjà.

**Raison.** Une copie figerait la correspondance du jour où elle a été faite. Corriger un rayon dans `CiqualCategories` n'atteindrait jamais les fiches déjà copiées, et **aucune migration ne pourrait le rattraper** : les deux bases sont deux fichiers, et une migration Room ne lit pas l'autre. Le rayon est une propriété de la **référence**, pas de la copie — à l'inverse exact des six valeurs, que le journal fige exprès ([D05](#d05--les-entrées-de-journal-figent-leurs-valeurs--validée)).

**Conséquence heureuse** : aucune colonne ajoutée à `food`, donc aucune migration, donc aucun problème de reprise sur une base existante. Le coût est une requête `IN (...)` par affichage.

**Écarté.** *Une colonne `category` dans `food`*, avec migration 2 → 3 : filtrage en SQL des deux côtés, mais les fiches déjà copiées seraient restées à `NULL` — donc muettes sous leur pastille — sans moyen honnête de les rattraper.

### Un tag seul est un mode parcours

Champ vide et une pastille : la liste des aliments du rayon. `FoodSearch.search` rend donc quelque chose pour une requête vide **si le filtre ne l'est pas** ; vides tous les deux, rien — le catalogue entier n'est pas une réponse.

**Les sections « Favoris » et « Récents » disparaissent dès qu'une pastille est active**, remplacées par la liste parcourue. Elles ne sont pas perdues pour autant : `FoodRanking` fait déjà remonter ce qui a un `useCount > 0`, donc « Fruits » liste les fruits qu'on mange d'abord. L'alternative — garder les deux sections filtrées **plus** la liste — faisait apparaître trois fois un fruit épinglé et récemment mangé.

**Une qualité écarte la table de l'ANSES.** Une ligne non versée au catalogue n'est ni personnelle ni épinglée ; l'interroger quand même rendrait des résultats que le filtre rejetterait juste après, et « Mon aliment » proposerait de supprimer une référence publiée.

### Un aliment personnel ne porte aucun rayon

Décision de l'auteur, appliquée telle quelle. **Contestation, pour mémoire** : la conséquence est que des « pâtes de mamie » ne sortent pas sous « Féculents », alors que l'utilisateur qui les a saisies sait très bien ce que c'est. Elle est acceptée parce que l'alternative — un sélecteur de catégorie au formulaire de création — ajoute un champ à chaque saisie manuelle pour un gain qui ne se voit qu'en mode parcours, et que le formulaire est déjà le point de friction du parcours. Le champ reste `null` en base : le jour où la question revient, il n'y a rien à migrer.

### Trois points d'outillage, dont un piège désamorcé

- **L'import échoue si la nomenclature bouge.** Même dispositif que `Nutrient` : le code fait foi, l'intitulé le vérifie. Deux dérives sont attrapées — une renumérotation, qui rangerait les poissons dans les desserts, et un **sous-groupe ajouté**, qui n'aurait aucun rayon sans que personne l'ait décidé. Un silence qui ressemble à un arbitrage est ce qu'il fallait rendre impossible.
- **Le nom du fichier copié porte une révision de schéma**, `ciqual-2025-11-03-r2.db`. Sans elle, ajouter une colonne sans que l'ANSES ait republié laissait le nom inchangé : un appareil déjà installé aurait gardé sa copie, et la première requête sur `category` aurait échoué **chez lui seulement** — jamais ici, où l'installation est toujours fraîche.
- **Le décompte par rayon est imprimé à chaque import.** 191 fruits, 352 légumes, 325 boissons, 754 sans rayon. Un rayon à douze aliments quand on en attendait deux cents se lit d'un coup d'œil, là où aucun test ne dirait qu'on s'est trompé de case.

**Ce que ça coûte.** `ciqual.db` passe de 824 à 924 Ko : la colonne porte le **nom** de l'énumération et non un entier, parce que la base est lue par une version de l'application qui n'est pas forcément celle qui l'a écrite, et qu'un client SQLite doit pouvoir la lire le jour où une pastille ne rend rien.

---

## Décisions prises par défaut, à confirmer

Ces points n'ont pas été arbitrés explicitement. J'ai tranché pour que la spécification soit complète et cohérente ; chacun se change sans rien casser à ce stade.

| # | Sujet | Retenu | Où |
|---|---|---|---|
| 1 | Formule de dépense | Mifflin-St Jeor | [03](03-nutrition-calculs.md) |
| 2 | Presets d'objectif | Perdre / Maintenir / Prendre | [02](02-parcours-et-ecrans.md#onboarding) |
| 3 | Garde-fous | 1 %/sem, ±25 % du TDEE, plancher 1200/1500 | [03](03-nutrition-calculs.md#garde-fous) |
| 5 | Séances de sport ponctuelles | Non — multiplicateur d'activité seulement | [03](03-nutrition-calculs.md#dépense-énergétique-totale-tdee) |
| 7 | Unités | g, ml, et portions nommées | [04](04-sources-de-donnees.md#portions-usuelles) |
| 8 | Récents | 20 aliments, tri par date d'usage | [02](02-parcours-et-ecrans.md#modale--recherche) |
| 9 | Plats favoris | Oui, réutilisables en un tap | [07](07-modele-de-donnees.md) |
| 10 | Saisie dans le futur | Non — aujourd'hui et passé uniquement | [02](02-parcours-et-ecrans.md) |
| 12 | Sucres | Sucres totaux, en plafond OMS 10 % | [03](03-nutrition-calculs.md#sucres) |
| 13 | Contribution à Open Food Facts | Hors v1, interface prévue | [04](04-sources-de-donnees.md#produit-absent) |
| 14 | Quantité par défaut au scan | Portion de l'emballage, sinon 100 g | [02](02-parcours-et-ecrans.md) |
| 15 | Fournisseurs d'IA | Gemini, OpenAI, Anthropic, DeepSeek, Mistral + compatible | [05](05-ia.md#fournisseurs) |
| 17 | Compteur de coût | Oui, estimation locale datée | [05](05-ia.md#coût) |
| 19 | Sans clé API | Modes IA visibles mais grisés, avec explication | [02](02-parcours-et-ecrans.md#modale--photo) |
| 21 | Sauvegarde Drive | Quotidienne en Wi-Fi, chiffrement optionnel désactivé par défaut | [09](09-donnees-et-sauvegarde.md) |
| 22 | Export local | JSON complet réimportable + CSV du journal | [09](09-donnees-et-sauvegarde.md#export-et-import-de-fichier) |
| 23 | Versions de sauvegarde | 5, en rotation | [09](09-donnees-et-sauvegarde.md#rotation) |
| 24 | Télémétrie | Aucune, y compris crash reporting | [01](01-perimetre.md#contraintes-fermes) |
| 26 | Progression | Hexagone en tête, barres pour les valeurs — voir D33 | [08](08-design-system.md#macrohexagon) |
| 27 | Langues | Français et anglais dès la 1.0 | [01](01-perimetre.md#plateforme) |
| 28 | Widget et notifications | Hors v1, widget en tête de la 1.1 | [10](10-qualite-et-livraison.md#feuille-de-route) |
| 29 | Android minimum | API 26 | [01](01-perimetre.md#plateforme) |
| 30 | Nom | **Hexaphore** — tranché, voir D13 ci-dessus | — |
| 31 | `applicationId` | `app.hexaphore` | [10](10-qualite-et-livraison.md#identité-de-lapplication) |
