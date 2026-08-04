# 04 — Sources de données alimentaires

Trois sources, trois rôles distincts, un catalogue local unifié qui les fait cohabiter.

| Source | Rôle | Accès | Licence |
|---|---|---|---|
| **CIQUAL 2025** (ANSES) | Aliments et plats génériques | Embarqué dans l'APK | Licence Ouverte Etalab 2.0 |
| **Open Food Facts** | Produits emballés, par code-barres | API en ligne + cache permanent | ODbL 1.0 |
| **Aliments personnels** | Ce que l'utilisateur crée lui-même | Base locale | — |

Pourquoi ce partage plutôt qu'une source unique : Open Food Facts référence des *produits de marque*. Chercher « lasagnes » y renvoie quarante barquettes industrielles, pas une lasagne. CIQUAL fait exactement l'inverse : des aliments et des plats représentatifs de la consommation française, avec des valeurs de référence, mais aucun code-barres. Les deux sont complémentaires, aucun ne remplace l'autre.

---

## CIQUAL

### La donnée

3 484 aliments, publiés par l'ANSES, avec les six macros dont l'application a besoin, plus les acides gras, minéraux et vitamines qu'elle stocke sans les afficher.

Colonnes utilisées, sous leur intitulé exact dans le fichier source :

| Champ interne | Colonne CIQUAL |
|---|---|
| `kcal100` | `Energie, Règlement UE N° 1169/2011 (kcal/100 g)` |
| `protein100` | `Protéines, N x facteur de Jones (g/100 g)` |
| `carb100` | `Glucides (g/100 g)` |
| `sugar100` | `Sucres (g/100 g)` |
| `fat100` | `Lipides (g/100 g)` |
| `fiber100` | `Fibres alimentaires (g/100 g)` |

### Import au build

Un script Gradle (`:tooling:ciqual-import`) transforme le XML de l'ANSES en base SQLite pré-construite, livrée dans `assets/ciqual.db` (~4 Mo).

Le fichier source brut est versionné dans le dépôt sous `tooling/ciqual/`, et la base générée l'est aussi : sans elle, un `git clone` suivi d'un build donnerait une application vide. Le script vérifie une empreinte SHA-256 pour détecter une regénération non intentionnelle.

### Le piège des valeurs textuelles

CIQUAL n'est pas un fichier de nombres. Les valeurs arrivent sous forme de chaînes, avec une virgule décimale et plusieurs conventions à interpréter. C'est **la** source de bugs sur ce jeu de données.

| Valeur brute | Interprétation | Justification |
|---|---|---|
| `12,5` | `12.5` | Virgule décimale française |
| `traces` | `0.0` | Présence négligeable |
| `< 0,5` | `0.25` | Milieu de l'intervalle, sans biaiser vers le haut |
| `-` | `null` | Non déterminé — **différent de zéro** |
| `NC` | `null` | Non communiqué |
| chaîne vide | `null` | Idem |

`null` et `0` ne sont pas interchangeables. Un aliment dont les fibres sont inconnues ne doit pas faire croire à zéro fibre : l'interface affiche « — » et le total de la journée signale que des lignes sont incomplètes.

Le parseur est isolé (`CiqualValueParser`), et chacune de ces lignes est un cas de test.

### Recherche

Table `ciqual_fts` en FTS5, tokenizer `unicode61 remove_diacritics 2`. C'est ce réglage qui fait que « creme brulee » trouve « crème brûlée » — indispensable pour une saisie au clavier mobile, où personne ne tape les accents.

La requête part dès le **2ᵉ caractère**, après 120 ms sans frappe ([02](02-parcours-et-ecrans.md#modale--recherche)). Deux caractères suffisent parce que la recherche est locale : le coût d'une requête inutile est une lecture SQLite, pas un aller-retour réseau.

Classement : score BM25, puis remontée des aliments courts et déjà consommés par l'utilisateur. Sans ce second critère, « pomme » renvoie « pomme de terre à chair farineuse, crue » avant « pomme, pulpe et peau, crue ».

### Portions usuelles

CIQUAL ne donne que des valeurs pour 100 g. Or personne ne pèse une pomme.

Le dépôt maintient donc `tooling/ciqual/servings.csv` — table écrite et relue à la main, associant un code CIQUAL à une ou plusieurs portions nommées :

```csv
code_ciqual,libelle,grammes,par_defaut
13039,1 pomme moyenne,150,true
22000,1 œuf,50,true
7003,1 tranche,30,true
7003,1 baguette,250,false
18066,1 verre,200,true
```

Environ 250 entrées pour les aliments les plus fréquents. C'est peu de travail et c'est ce qui fait la différence entre une application utilisable et une balance de cuisine obligatoire. Cette table est explicitement ouverte aux contributions : c'est le point d'entrée idéal pour quelqu'un qui veut aider sans écrire de Kotlin.

Un aliment sans portion déclarée propose 100 g par défaut.

---

## Open Food Facts

### Appel

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
    ?fields=code,product_name,product_name_fr,brands,quantity,
            serving_size,serving_quantity,nutriments,image_front_small_url
```

**En-tête `User-Agent` obligatoire** : `Hexaphore/<version> (github.com/hexaphore/hexaphore)`. Open Food Facts bloque les clients anonymes, et c'est légitime : l'en-tête est leur seul moyen de joindre l'auteur d'un client qui se comporte mal. Cet en-tête est posé par un intercepteur OkHttp, pas au cas par cas.

L'adresse est figée sur l'organisation GitHub du projet, réservée pour cela ([D14](11-decisions.md#d14--domaine-et-publication-reportés-après-la-05--validée)) : elle survit à un changement de propriétaire, contrairement à un compte personnel. La version vient du `versionName`, pour qu'un rapport de la part d'Open Food Facts désigne un binaire précis.

Pas de clé, pas de compte, pas de quota commercial. Limite de courtoisie : 100 requêtes par minute — hors d'atteinte pour un usage normal, mais l'intercepteur applique quand même un retrait exponentiel sur `429` et `5xx`, trois tentatives maximum.

### Champs récupérés

| Champ interne | Chemin JSON | Absent ? |
|---|---|---|
| `name` | `product_name_fr` sinon `product_name` | Bloquant : sans nom, la fiche est inutilisable |
| `brand` | `brands` (première valeur avant la virgule) | Toléré |
| `kcal100` | `nutriments["energy-kcal_100g"]`, sinon `nutriments["energy_100g"]` ÷ 4,184 | Toléré |
| `protein100` | `nutriments.proteins_100g` | Toléré |
| `carb100` | `nutriments.carbohydrates_100g` | Toléré |
| `sugar100` | `nutriments.sugars_100g` | Toléré |
| `fat100` | `nutriments.fat_100g` | Toléré |
| `fiber100` | `nutriments.fiber_100g` | Toléré — **manquant très souvent** |
| `servingG` | `serving_quantity`, sinon parsing de `serving_size` | Toléré |

Deux réflexes obligatoires face à cette base collaborative :

1. **L'énergie peut n'exister qu'en kilojoules.** Conversion `kJ ÷ 4,184`, et jamais l'inverse : si `energy-kcal_100g` existe, il fait foi.
2. **Tout champ nutritionnel peut être absent.** La fiche est quand même créée, en état *incomplet*. L'écran de validation met les trous en évidence et invite à les compléter à la main ; la valeur saisie est conservée localement pour toujours.

### Cache

Toute fiche récupérée est écrite dans le catalogue local (`food`, `source = OFF`) et n'en sort plus. Conséquences : un produit scanné une fois est disponible hors-ligne à vie, et le second scan est instantané.

Rafraîchissement : proposé, jamais imposé, sur une fiche de plus de 90 jours ouverte manuellement. Un rafraîchissement **n'écrase jamais** un champ que l'utilisateur a corrigé à la main, ni les macros déjà figées dans des entrées de journal.

### Produit absent

Environ 1 produit sur 10 en France, davantage sur les marques régionales et les produits frais. Le parcours est décrit en [02](02-parcours-et-ecrans.md#modale--scan-de-code-barres) ; l'essentiel est que l'aliment créé à la main conserve son code-barres, et devient donc scannable comme n'importe quel autre.

**Point d'extension prévu** : contribuer la fiche à Open Food Facts. L'interface `FoodContributionTarget` est définie dès la v1 et n'a aucune implémentation — c'est un contrat, pas du code mort, et il documente l'intention.

---

## Aliments personnels

Créés depuis la recherche (« Créer *« … »* »), depuis un scan infructueux, ou depuis les réglages. Champs : nom, marque, macros pour 100 g, portions nommées, code-barres facultatif.

Ils sont prioritaires dans les résultats de recherche : ce que l'utilisateur a pris la peine de saisir est ce qu'il mange vraiment.

Un aliment personnel utilisé dans le journal ne peut pas être supprimé sans confirmation ; et sa suppression n'efface pas l'historique, les macros y étant figées ([07](07-modele-de-donnees.md)).

---

## Résolution : du texte de l'IA à un aliment

Étape charnière entre [05](05-ia.md) et le journal. L'IA rend `{ « jus d'orange », 200, ML }` ; il faut en tirer des macros.

`NutritionResolver` applique quatre étapes.

**1. Normalisation.** Minuscules, accents retirés, ponctuation supprimée, pluriels naïfs français (`-s`, `-x`, `-aux` → `-al`), articles de tête retirés (« du », « de la », « un »).

**2. Candidats.** Recherche FTS sur les trois sources, fusionnée, avec ces poids :

| Origine | Multiplicateur |
|---|---|
| Aliment déjà consommé par l'utilisateur | × 1,5 |
| Aliment personnel | × 1,3 |
| CIQUAL | × 1,0 |
| Produit Open Food Facts en cache | × 0,8 |

Le produit de marque est délibérément dévalorisé : « riz » doit tomber sur le riz CIQUAL, pas sur un paquet de riz scanné il y a trois mois.

**3. Décision.**

- score ≥ **0,75** → correspondance retenue automatiquement ;
- **0,40 – 0,75** → meilleur candidat retenu, mais la ligne est signalée et propose jusqu'à 3 alternatives ;
- < **0,40** → aucune correspondance, on passe à l'étape 4.

**4. Repli IA.** Toutes les lignes non résolues partent en **un seul second appel** groupé, qui demande une estimation des macros pour 100 g. Le résultat est marqué `source = AI_ESTIMATE`, porte un badge distinct dans l'interface, et n'est jamais versé au catalogue partagé — c'est une estimation, pas une référence.

Sans clé API valide à ce moment-là, la ligne est présentée à zéro avec une invitation à la compléter ou à la remplacer.

### Conversion des quantités

```
G      → grammes tels quels
ML     → grammes × densité (1,00 par défaut ; 1,04 jus ; 1,03 lait ; 0,92 huile)
PIECE  → table des portions usuelles ; à défaut, portion par défaut de l'aliment ; à défaut 100 g
SLICE  → table des portions ; à défaut 30 g
TBSP   → 15 g × densité      TSP → 5 g × densité
BOWL   → 250 g               PLATE → 350 g       GLASS → 200 g × densité
```

Toute conversion appuyée sur un défaut plutôt que sur une donnée réelle est signalée dans l'écran de validation. L'utilisateur doit voir quand l'application devine.

---

## Licences et obligations

Ce ne sont pas des formalités : elles conditionnent ce que le projet a le droit de faire.

**CIQUAL — Licence Ouverte Etalab 2.0.** Réutilisation libre, y compris commerciale. Obligation : citer la paternité et la date de version. L'écran « À propos » affiche *« Table de composition nutritionnelle CIQUAL 2025 — ANSES »*.

**Open Food Facts — ODbL 1.0.** Trois obligations, dont deux souvent ignorées :

1. **Attribution** — mention visible dans « À propos ».
2. **Partage à l'identique** — toute *base de données dérivée* qui serait redistribuée doit l'être sous ODbL. Ici, l'application ne redistribue aucune base : elle interroge l'API et met en cache pour l'utilisateur. Aucune obligation ne se déclenche.
3. **Maintien de la clause** — si une base dérivée était un jour publiée (un jeu de portions agrégé, par exemple), elle devrait porter la même licence.

**Sauvegardes et exports.** Un fichier de sauvegarde contient des fiches issues d'Open Food Facts. Il est produit *par* l'utilisateur, *pour* l'utilisateur, et n'est pas publié : ce n'est pas une redistribution au sens ODbL. La mention de source est néanmoins incluse dans l'en-tête du fichier exporté — coût nul, ambiguïté levée.

**Interdiction ferme.** Ne jamais publier dans le dépôt un extrait massif de la base Open Food Facts. Le cache est local, produit à l'usage, et n'est pas versionné.
