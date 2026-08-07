# 10 — Qualité et livraison

## Stratégie de test

Pyramide classique, mais avec un déséquilibre assumé : l'essentiel de l'effort porte sur `:domain`, parce que c'est là que les erreurs coûtent cher et que les tests coûtent le moins.

| Niveau | Portée | Outillage | Objectif |
|---|---|---|---|
| Unitaire JVM | `:domain`, correspondances, parseurs | JUnit 5, Turbine, MockK | **couverture ≥ 90 %** sur `:domain` |
| Base de données | DAO, migrations | Room testing, Robolectric | toutes les migrations, toutes les requêtes |
| Composant UI | Composants du design system | Compose UI test | états et accessibilité |
| Image de référence | Écrans complets | Roborazzi | non-régression visuelle |
| Bout en bout | 5 parcours critiques | Compose UI test sur émulateur | ils ne cassent jamais |

### Ce qui doit être couvert sans exception

Ces cas ont été identifiés pendant la conception comme les endroits où un bug passe inaperçu et fait des dégâts durables :

- **Parseur CIQUAL** — `traces`, `< 0,5`, `-`, `NC`, virgule décimale, chaîne vide. Un `null` traité comme `0` fausse des mois de journal ([04](04-sources-de-donnees.md)).
- **Calculs d'objectif** — chaque garde-fou, ses deux bornes, et l'exemple complet de [03](03-nutrition-calculs.md#exemple-complet) comme test de référence.
- **Conversions d'unités** — chaque unité, chaque densité, chaque repli sur valeur par défaut.
- **Correspondance Open Food Facts** — champs absents, énergie en kJ seulement, `serving_size` non parsable, produit sans nom.
- **Robustesse du parsing IA** — JSON entouré de texte, tronqué, unité inconnue, confiance hors bornes, tableau vide.
- **Migrations Room** — chaîne complète `1 → N` sur une base peuplée.
- **Format de sauvegarde** — aller-retour export/import à l'identique, et refus d'une `formatVersion` supérieure.
- **Frontière jour/nuit** — une entrée à 23 h 59 appartient au bon jour, y compris au changement d'heure. `Clock` injecté partout ([06](06-architecture.md#cas-dusage)).

### Parcours de bout en bout

Cinq, pas plus — ils sont lents et fragiles, on les réserve à ce qui ne doit jamais casser :

1. Onboarding complet → objectifs calculés et affichés.
2. Recherche → ajout → visible dans le journal du jour avec les bons totaux.
3. Édition manuelle d'une entrée → totaux mis à jour, valeur verrouillée.
4. Navigation vers un jour passé → ajout → retour à aujourd'hui sans perte.
5. Export → effacement → import → état identique.

Le scan et l'IA ne sont pas testés de bout en bout : ils dépendent de la caméra et d'un service tiers. Leurs adaptateurs sont testés unitairement contre des réponses enregistrées.

---

## Analyse statique

| Outil | Rôle | Bloquant en CI |
|---|---|---|
| **ktlint** | Formatage | oui |
| **detekt** | Complexité, code mort, mauvaises pratiques | oui |
| **Android Lint** | Spécifique Android, accessibilité | avertissements bloquants sur les règles a11y |
| **Dependency Analysis** | Dépendances déclarées inutiles ou manquantes | non, rapport |

Trois règles detekt personnalisées, qui encodent les décisions de cette documentation au lieu de compter sur la vigilance :

1. **Pas de couleur codée en dur** hors `:core:designsystem` — interdit `Color(0x…)` ailleurs ([08](08-design-system.md#ressources)).
2. **Pas d'import Android dans `:domain`** — doublon volontaire de la contrainte Gradle, avec un message d'erreur qui explique pourquoi.
3. **Pas de `System.currentTimeMillis()` ni de `LocalDate.now()`** hors des implémentations de `Clock` — c'est ce qui garantit que le temps reste testable.

Ces règles vivent dans `build-logic/detekt-rules` et sont couvertes par leurs propres tests unitaires : une règle qu'on croit active sans l'avoir éprouvée ne protège rien.

**detekt analyse tous les jeux de sources, pas seulement `main` et `test`.** Sa tâche par défaut ne regarde que ces deux-là ; la source est donc fixée explicitement à `src` dans le `build.gradle.kts` racine. Sans cette ligne, déplacer un fichier de `src/main` vers `src/debug` le sortait de l'analyse sans qu'aucun build ne le signale ([D38](11-decisions.md)).

**Après avoir modifié une règle, `./gradlew --stop`.** detekt s'exécute dans un worker dont le chargeur de classes est mis en cache par le démon Gradle, et le chemin du jar de règles ne change pas d'un build à l'autre : le démon continue donc d'appliquer l'ancienne version, sans rien signaler. Les tests du module, eux, voient toujours le code à jour — c'est ce décalage qui rend le piège coûteux. La CI n'est pas concernée, elle démarre sur un démon neuf.

**Ce que l'outillage ne couvre pas.** La définition de « terminé » exige qu'aucune couleur, **durée ou dimension** ne soit codée en dur hors de `:core:designsystem`. Seules les couleurs sont vérifiées par une règle. Une règle sur les littéraux `.dp` et `.sp` a été écartée : en Compose, elle signale autant de faux positifs que de vrais, et une règle qu'on finit par désactiver est pire qu'une règle absente. Les durées et les dimensions restent donc tenues par la revue — c'est une faiblesse connue, écrite ici pour ne pas être découverte plus tard.

---

## Intégration continue

GitHub Actions.

**Sur chaque pull request** — cible : moins de 8 minutes.

```
ktlint → detekt → tests unitaires JVM → tests Room (Robolectric)
       → tests d'image → assembleDebug → rapport de couverture
```

**Sur `main`** : ce qui précède, plus les tests instrumentés sur émulateur API 26 et API 34 (la borne basse et une borne haute — les bugs de compatibilité vivent aux extrémités).

**Sur un tag `v*`** : build release signé, `bundleRelease` pour le Play Store, APK universel pour GitHub Releases, notes de version extraites du `CHANGELOG`, publication en brouillon sur la piste interne du Play Store.

Les clés de signature sont des secrets de dépôt. Le trousseau n'est **jamais** versionné, et une règle `.gitignore` explicite le rappelle.

---

## Variantes de build

Deux `productFlavors` sur la dimension `distribution`, parce que le règlement du Play Store interdit les liens de don externes pour un développeur qui n'est pas une association reconnue.

| | `github` | `play` |
|---|---|---|
| Lien de don | affiché | absent du binaire |
| Vérification de mise à jour | comparaison à la dernière release GitHub | déléguée au Play Store |
| Sauvegarde Drive | oui | oui |
| Identifiant applicatif | identique | identique |

Le lien de don n'est pas masqué à l'exécution : il est absent de la variante `play`, via une implémentation différente de `DonationLinkProvider`. Un contenu simplement caché reste dans l'APK et reste détectable lors de l'examen.

Types de build : `debug` (journalisation détaillée, suffixe `.debug` pour cohabiter), `release` (R8 avec réduction de ressources, journalisation neutralisée).

---

## Identité de l'application

Trois identifiants à ne pas confondre.

| | Exemple | Modifiable ? |
|---|---|---|
| **Nom affiché** | Hexaphore | oui, à tout moment |
| **`applicationId`** | `app.hexaphore` | **non**, dès la première publication sur le Play Store |
| **Paquets Kotlin** | `app.hexaphore.feature.home` | oui, simple remaniement |

L'`applicationId` est l'identité de l'application pour Android et pour le Play Store — c'est lui qu'on lit dans l'URL d'une fiche (`play.google.com/store/apps/details?id=…`). Le changer après publication crée **une application entièrement nouvelle** : nouvelle fiche, zéro installation, et les utilisateurs existants ne reçoivent plus aucune mise à jour. Aucune procédure de renommage n'existe.

Valeur retenue : **`app.hexaphore`**, DNS inversé de `hexaphore.app`.

Deux précisions qui évitent des malentendus :

- **Rien ne vérifie ce nom.** Android ne fait aucune résolution DNS ; la convention du DNS inversé n'existe que pour garantir l'unicité mondiale. L'utiliser avant de posséder le domaine ne casse rien.
- **Mais le domaine doit être sécurisé avant la première publication.** Tant que rien n'est sur le Play Store, changer d'identifiant reste un remaniement mécanique. Après, c'est définitif.

L'identifiant ne mentionne ni pseudonyme, ni hébergeur : il survit à un changement de propriétaire, à un passage en association, ou à un départ de GitHub.

---

## Versionnement

**SemVer** pour le nom (`1.4.2`), `versionCode` entier strictement croissant, calculé depuis le tag Git.

`CHANGELOG.md` au format Keep a Changelog, rédigé pour des utilisateurs et non pour des développeurs : *« Le scan reconnaît maintenant les codes-barres à 8 chiffres »*, pas *« refactor du BarcodeAnalyzer »*.

Les notes du Play Store sont générées depuis le changelog via les métadonnées Fastlane, versionnées dans `fastlane/metadata/android/{fr-FR,en-US}/`.

---

## Feuille de route

Ordre choisi pour qu'une version utilisable existe le plus tôt possible, et que chaque étape soit livrable même si la suivante n'arrive jamais.

| Version | Contenu | Livrable |
|---|---|---|
| **0.1** | Modèle de données, onboarding, calcul d'objectifs, journal manuel, recherche CIQUAL, design system | Déjà utilisable au quotidien |
| **0.2** | Scan de code-barres, Open Food Facts, cache, aliments personnels | Le mode de saisie le plus fréquent |
| **0.3** | IA photo et texte, fournisseurs, résolution, écran de validation | La fonctionnalité différenciante |
| **0.4** | Calendrier étendu, journée passée, journal de poids, adaptation hebdomadaire | Le suivi dans la durée |
| **0.5** | Sauvegarde Drive, export/import, chiffrement optionnel | Les données deviennent sûres |
| **1.0** | Accessibilité, traduction anglaise, tests d'image, `LICENSE`, `CONTRIBUTING` | Application finie, distribuée en APK via GitHub Releases |
| **1.1** | Widget, favoris avancés, contribution à Open Food Facts | |
| **Play** | Domaine, politique de confidentialité, formulaire Data Safety, compte développeur, 12 testeurs pendant 14 jours | Étape distincte et facultative, déclenchée quand l'application le mérite — voir D14 dans [11](11-decisions.md) |

La publication sur le Play Store est délibérément sortie de la 1.0 : elle dépend d'un domaine, d'un compte payant et d'un recrutement de testeurs, c'est-à-dire de trois choses qui n'ont rien à voir avec la qualité du logiciel. Une 1.0 distribuée en APK est une vraie 1.0.

La 0.1 contient déjà le design system complet. Repousser le style à la fin d'un projet « néon » revient à ne jamais le faire, et à découvrir trop tard que la structure des écrans ne s'y prête pas.

---

## Contribution

`CONTRIBUTING.md` couvre :

- Comment construire le projet (JDK 17, Android Studio, `./gradlew assembleGithubDebug`).
- La règle des dépendances : `:domain` reste pur, tout PR qui y ajoute une dépendance Android est refusé sans discussion.
- La table des portions (`servings.csv`) comme point d'entrée pour contribuer sans écrire de Kotlin — c'est le fichier le plus utile et le plus accessible du dépôt.
- Format des messages de commit : Conventional Commits, qui alimente le changelog.
- Ce qui ne sera pas accepté : SDK d'analytics, dépendance à un service payant, collecte de données, fonctionnalité exigeant un serveur.

Modèles d'issue : bogue (avec version, appareil, étapes), demande de fonctionnalité (avec le besoin avant la solution), donnée nutritionnelle incorrecte (redirigée vers Open Food Facts ou vers `servings.csv` selon la source).

---

## Documentation

Ce dossier `docs/` **est** la documentation de conception. Il est versionné avec le code et mis à jour dans le même PR que le changement qu'il décrit. Une documentation qui dérive du code est pire que pas de documentation : elle ment avec autorité.

Dans le code, on documente **pourquoi**, jamais **quoi** :

```kotlin
// Non : le code le dit déjà.
/** Calcule le BMR. */

// Oui : ça, le code ne le dit pas.
/**
 * Mifflin-St Jeor. Retenue plutôt que Harris-Benedict pour sa meilleure précision sur les
 * populations actuelles, et plutôt que Katch-McArdle qui exige un taux de masse grasse
 * dont on ne dispose pas. Marge d'erreur ≈ ±10 % : c'est l'ajustement hebdomadaire qui
 * corrige, voir docs/03-nutrition-calculs.md.
 */
```

KDoc obligatoire sur tout ce qui est public dans `:domain` et sur chaque port. Ailleurs, au jugé — un nom clair vaut mieux qu'un commentaire qui l'explique.
