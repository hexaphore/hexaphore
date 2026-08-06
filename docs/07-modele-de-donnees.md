# 07 — Modèle de données

Base Room `hexaphore.db`, plus une base annexe `ciqual.db` livrée en lecture seule dans les assets.

## Vue d'ensemble

```
profile ──1:N── weight_entry
   │
   └──1:N── goal (versionné)

dish ──1:N── food_entry ──N:1── food ──1:N── food_serving
                                   │
favorite_dish ──1:N── favorite_component ──N:1──┘
```

## Conventions

- Identifiants : `String` UUIDv4, générés côté application. Un entier auto-incrémenté rendrait toute fusion de sauvegardes impossible et interdirait les identifiants stables entre appareils.
- Dates sans heure : `LocalDate` en `TEXT` ISO-8601 (`2026-08-02`). Triables en SQL, lisibles à l'œil dans un export.
- Instants : `Instant` en `INTEGER` (millisecondes UTC).
- Poids et masses : `REAL`, toujours en **grammes** ou **kilogrammes**. Le système impérial est une conversion d'affichage, jamais de stockage — sinon les arrondis s'accumulent.
- Tout `created_at` / `updated_at` en millisecondes UTC : ce sont eux qui rendront une fusion de sauvegardes possible un jour.

---

## Les deux invariants qui structurent tout

### 1. Une entrée de journal fige ses macros

`food_entry` **duplique** les six valeurs nutritionnelles au moment de l'enregistrement, au lieu de les recalculer depuis `food`.

C'est une dénormalisation assumée. Sans elle :

- un fabricant reformule son produit, Open Food Facts est mis à jour, et le journal d'il y a six mois change tout seul ;
- une correction de valeur repeint rétroactivement tout l'historique ;
- la suppression d'un aliment personnel effacerait des repas déjà consommés.

Un journal alimentaire est un **registre d'événements**. Ce qui est écrit est écrit.

`food_id` reste renseigné, mais comme un lien de provenance — utile pour « ré-ajouter la même chose », inutile au calcul.

### 2. Un objectif n'est jamais modifié, il est remplacé

Voir [03](03-nutrition-calculs.md#versionnement-des-objectifs). Chaque changement crée une ligne ; l'ancienne reçoit `ended_at`. La journée du 12 mars est jugée avec l'objectif du 12 mars.

---

## Tables

### `profile`

Ligne unique, `id = "singleton"`.

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | toujours `"singleton"` |
| `birth_date` | TEXT | l'âge se déduit, il ne se stocke pas |
| `sex` | TEXT | `MALE` · `FEMALE` · `UNSPECIFIED` |
| `height_cm` | REAL | |
| `activity_level` | TEXT | `SEDENTARY` … `VERY_ACTIVE` |
| `unit_system` | TEXT | `METRIC` · `IMPERIAL` — affichage seulement |
| `created_at`, `updated_at` | INTEGER | |

Stocker la date de naissance et non l'âge évite un objectif qui se périme silencieusement.

### `weight_entry`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `date` | TEXT | **UNIQUE** — une pesée par jour, la dernière remplace |
| `weight_kg` | REAL | |
| `created_at` | INTEGER | |

Index sur `date DESC` : toutes les lectures sont des fenêtres récentes.

### `goal`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `started_at` | TEXT | date de prise d'effet |
| `ended_at` | TEXT NULL | `NULL` = objectif courant |
| `origin` | TEXT | `CALCULATED` · `MANUAL` · `ADJUSTMENT` |
| `target_weight_kg` | REAL NULL | |
| `target_date` | TEXT NULL | |
| `strategy` | TEXT | `LOSE` · `MAINTAIN` · `GAIN` |
| `kcal` | INTEGER | |
| `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g` | REAL | |
| `manual_fields` | TEXT | liste des champs édités à la main, séparés par `,` |
| `created_at` | INTEGER | |

`manual_fields` protège le travail de l'utilisateur : un recalcul ne réécrit pas un champ qu'il a fixé lui-même, sauf accord explicite.

**Invariant** : au plus une ligne avec `ended_at IS NULL`. Vérifié par un index unique partiel et par un test de migration.

### `food`

> **Créée en tranche 3**, avec l'import CIQUAL qui la remplit. Rien ne l'écrit ni ne la lit avant : une entrée de journal fige ses macros et n'a pas besoin de la fiche d'origine pour s'afficher. La colonne `food_entry.food_id` arrive avec elle ([D34](11-decisions.md)).

Catalogue unifié : CIQUAL importé à la demande, produits Open Food Facts mis en cache, aliments personnels.

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `source` | TEXT | `CIQUAL` · `OFF` · `CUSTOM` |
| `source_ref` | TEXT NULL | code CIQUAL ou code-barres |
| `name` | TEXT | |
| `brand` | TEXT NULL | |
| `kcal_100` | REAL NULL | |
| `protein_100`, `carb_100`, `sugar_100`, `fat_100`, `fiber_100` | REAL NULL | |
| `saturated_fat_100`, `salt_100` | REAL NULL | stockés, non affichés en v1 |
| `default_serving_g` | REAL NULL | |
| `density` | REAL | g/ml, défaut `1.0` |
| `is_liquid` | INTEGER | |
| `user_edited_fields` | TEXT | protégé contre l'écrasement au rafraîchissement |
| `last_used_at` | INTEGER NULL | alimente « Récents » |
| `use_count` | INTEGER | alimente le classement de recherche |
| `is_favorite` | INTEGER | |
| `fetched_at` | INTEGER NULL | pour la péremption du cache Open Food Facts |
| `created_at`, `updated_at` | INTEGER | |

`NULL` signifie **inconnu**, jamais zéro. L'interface affiche « — » et non « 0 g ». Cette distinction est testée : c'est l'erreur la plus facile à introduire et la plus difficile à repérer.

Index unique sur `(source, source_ref)` quand `source_ref` n'est pas nul — empêche le doublon au double scan.

Les colonnes `saturated_fat_100` et `salt_100` existent alors que la v1 ne les affiche pas : la donnée est disponible dans les deux sources, la collecter maintenant coûte zéro et évite une migration le jour où on décide de les montrer.

### `food_serving`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `food_id` | TEXT FK → `food.id`, CASCADE | |
| `label` | TEXT | « 1 tranche », « 1 verre » |
| `grams` | REAL | |
| `is_default` | INTEGER | |

### `dish`

Un **plat** : plusieurs aliments, entrés en une fois. Pas de repas nommé, pas de catégorie à choisir avant d'enregistrer ([D31](11-decisions.md)).

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `date` | TEXT | journée locale à laquelle le plat est rattaché |
| `source` | TEXT | `MANUAL` · `SEARCH` · `BARCODE` · `PHOTO_AI` · `TEXT_AI` · `FAVORITE` |
| `logged_at` | INTEGER | ordonne les plats de la journée |
| `created_at`, `updated_at` | INTEGER | |

Index sur `(date, logged_at)` : c'est l'ordre d'affichage de l'accueil.

**`source` n'est jamais réécrite.** Un plat reste éditable à la main indéfiniment ; son origine est un fait historique, pas un état. Corriger une quantité sur une proposition de l'IA ne doit pas la faire passer pour une saisie manuelle — ce serait perdre la seule trace de ce qui a été deviné ([D32](11-decisions.md)).

Une journée sans saisie ne produit aucune ligne, ce qui permet de distinguer « rien mangé de noté » de « journée à zéro » ([02](02-parcours-et-ecrans.md#calendrier-étendu)).

### `food_entry`

| Colonne | Type | Notes |
|---|---|---|
| `id` | TEXT PK | |
| `dish_id` | TEXT FK → `dish.id`, CASCADE | |
| `food_id` | TEXT NULL FK → `food.id`, SET NULL | provenance, pas source de calcul |
| `display_name` | TEXT | figé — survit à la suppression de l'aliment |
| `quantity` | REAL | |
| `unit` | TEXT | unité choisie par l'utilisateur |
| `grams` | REAL | quantité convertie, base de tout calcul |
| `kcal` | REAL | **figé** |
| `protein_g`, `carb_g`, `sugar_g`, `fat_g`, `fiber_g` | REAL NULL | **figés** |
| `ai_confidence` | REAL NULL | |
| `is_manually_edited` | INTEGER | verrouille tout recalcul |
| `created_at`, `updated_at` | INTEGER | |

Index sur `dish_id`.

**Aucune source ici.** Elle appartient au plat : une ligne n'entre jamais dans le journal toute seule. La distinction « ce chiffre vient de CIQUAL » contre « ce chiffre est une estimation » reste nécessaire — [05](05-ia.md) prévoit qu'un plat photographié résolve certaines lignes dans les bases et estime les autres — mais elle arrive **en tranche 6**, avec le résolveur qui la produit, sous la forme minimale d'un marqueur `is_estimated`. Une colonne que rien ne remplit n'est pas une préparation, c'est du bruit.

### `favorite_dish` et `favorite_component`

Un plat enregistré pour être rejoué. C'est le seul endroit où un nom est demandé : un favori sans nom serait introuvable.

| `favorite_dish` | Type |
|---|---|
| `id` | TEXT PK |
| `name` | TEXT |
| `use_count` | INTEGER |
| `created_at` | INTEGER |

| `favorite_component` | Type |
|---|---|
| `id` | TEXT PK |
| `favorite_id` | TEXT FK, CASCADE |
| `food_id` | TEXT FK |
| `quantity`, `unit`, `grams` | REAL / TEXT / REAL |

Un favori référence des aliments **vivants** : « mes flocons du matin » doit refléter la fiche courante quand on le rejoue. Il produit ensuite des `food_entry` qui, eux, figent leurs valeurs. La différence de traitement est intentionnelle — un modèle réutilisable d'un côté, un registre d'événements de l'autre.

### `app_state`

Table clé-valeur pour ce qui ne mérite pas sa table : date de dernière sauvegarde, dernière suggestion d'ajustement présentée, version de prompt en cours, drapeau d'onboarding.

Les préférences utilisateur vont dans DataStore, pas ici. Cette table ne contient que de l'état technique, sauvegardé avec les données.

---

## Base CIQUAL embarquée

`assets/ciqual.db`, lecture seule, jamais migrée — remplacée en bloc à chaque nouvelle version de la table ANSES.

```sql
CREATE TABLE ciqual_food (
    code TEXT PRIMARY KEY, name TEXT NOT NULL, group_name TEXT,
    kcal_100 REAL, protein_100 REAL, carb_100 REAL,
    sugar_100 REAL, fat_100 REAL, fiber_100 REAL,
    saturated_fat_100 REAL, salt_100 REAL, density REAL
);
CREATE VIRTUAL TABLE ciqual_fts USING fts5(
    name, content='ciqual_food', content_rowid='rowid',
    tokenize='unicode61 remove_diacritics 2'
);
CREATE TABLE ciqual_serving (
    code TEXT, label TEXT, grams REAL, is_default INTEGER
);
```

Un aliment CIQUAL est **copié** dans `food` la première fois qu'il est réellement consommé. Copier les 3 484 lignes à l'installation gonflerait la base, les sauvegardes et la recherche avec 99 % de contenu jamais utilisé.

---

## Requêtes structurantes

**Résumé d'un jour** — la requête la plus fréquente de l'application, appelée à chaque défilement du calendrier :

```sql
SELECT d.id, d.source, d.logged_at,
       SUM(e.kcal)      AS kcal,
       SUM(e.protein_g) AS protein,
       SUM(e.carb_g)    AS carb,
       SUM(e.sugar_g)   AS sugar,
       SUM(e.fat_g)     AS fat,
       SUM(e.fiber_g)   AS fiber
FROM dish d LEFT JOIN food_entry e ON e.dish_id = d.id
WHERE d.date = :date
GROUP BY d.id ORDER BY d.logged_at;
```

⚠️ `SUM` traite `NULL` comme absent, ce qui est correct, mais **perd l'information qu'une valeur manquait**. Le domaine remonte donc les lignes et agrège lui-même, en retenant quels totaux sont minorés ([D29](11-decisions.md)). Cette requête sert au calendrier, où seul l'ordre de grandeur compte.

**Objectif actif à une date** :

```sql
SELECT * FROM goal
WHERE started_at <= :date AND (ended_at IS NULL OR ended_at > :date)
ORDER BY started_at DESC LIMIT 1;
```

**Récents** : `food` trié par `last_used_at DESC`, limité à 20, `WHERE last_used_at IS NOT NULL`.

Toutes les requêtes d'affichage renvoient un `Flow` : Room notifie sur invalidation, aucun rafraîchissement manuel n'est nécessaire.

---

## Migrations

Migrations Room explicites, **jamais** `fallbackToDestructiveMigration`. Perdre le journal alimentaire d'un utilisateur est une faute non rattrapable.

- Schémas exportés dans `:core:database/schemas/`, versionnés dans Git.
- Chaque migration testée avec `MigrationTestHelper` : on part du schéma N avec des données réelles, on migre, on vérifie le contenu — pas seulement que ça ne plante pas.
- Un test parcourt toute la chaîne `1 → N` sur une base peuplée.

Règle de conception : **préférer une colonne nullable à une table nouvelle**, et une table nouvelle à un renommage. Le renommage est ce qui casse les sauvegardes.

---

## Empreinte

Estimation pour un utilisateur régulier sur un an :

| | Volume |
|---|---|
| `food_entry` | ~5 500 lignes (15/jour) ≈ 1,5 Mo |
| `food` | ~600 aliments ≈ 0,3 Mo |
| `weight_entry` | ~300 lignes, négligeable |
| Base applicative | **≈ 2 Mo** |
| `ciqual.db` (assets) | ≈ 4 Mo |
| Sauvegarde compressée | **< 400 Ko** |

Aucune stratégie d'archivage n'est nécessaire à cette échelle, et aucune n'est prévue : ce serait de la complexité pour un problème qui n'existe pas.
