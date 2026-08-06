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

## D27 — Objectif ou limite : la nature appartient à la macro · ✓ validée

**Contexte.** Constaté sur appareil : les six jauges se ressemblent trop. [08](08-design-system.md) ne distinguait qu'un seul plafond, les sucres, et le reste se remplissait de la même façon. Une jauge de sucres qui monte ressemble alors à une réussite — le contresens exact que la distinction était censée empêcher.

**Choix.** Trois objectifs — **calories, protéines, fibres** — et trois limites — **glucides, sucres, lipides**. La nature est portée par l'énumération `Macro` dans `:domain`, pas par un paramètre de composant.

**Pourquoi dans le domaine.** C'est une règle nutritionnelle, pas un choix d'affichage. Tant qu'elle est un paramètre, elle est un oubli possible : il suffit qu'un écran instancie une barre de sucres sans le préciser pour que le contresens revienne. Portée par la macro, la question ne se pose plus.

**Pourquoi les lipides du côté des limites.** Ils ont bien un plancher physiologique de 0,6 g/kg, mais il est appliqué **au calcul de l'objectif**, une fois pour toutes. Au jour le jour, l'utilisateur n'a rien à atteindre : il a un budget à ne pas dépasser.

**Conséquences.** Trois signaux redondants distinguent les deux familles : le suffixe `max` sur la valeur, le comportement de la jauge, et la phrase annoncée par TalkBack — « sur un objectif de » contre « sur une limite de ». Le paramètre `mode` de `MacroBar` disparaît de l'API publique : il n'y avait aucune raison légitime de le forcer.

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

**Le dépassement rétrécit la figure.** Le contour de l'objectif n'est pas la limite du dessin ; c'est le dessin entier qui se met à l'échelle pour que le plus grand débordement tienne. L'hexagone cible qui rapetisse **est** le signal. Plafonné à 200 % — sans quoi une saisie erronée à 2 000 % réduirait la cible à un point, précisément au moment où il faut la lire pour corriger.

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
