# 08 — Design system

## Le parti pris

Néon sur noir profond. Mais un néon **fonctionnel** : la lumière sert à hiérarchiser l'information, pas à décorer.

Trois règles qui séparent un néon élégant d'un néon fatigant :

1. **Une seule chose brille à la fois par zone.** Si tout brille, plus rien ne ressort.
2. **Le fond reste sombre et neutre.** Aucune couleur saturée en aplat large — la couleur vit dans les traits, les anneaux et les lueurs.
3. **La couleur porte un sens et un seul.** Chaque macro a sa teinte, partout, sans exception. Une couleur qui change de signification d'un écran à l'autre annule tout le bénéfice.

---

## Couleurs

### Fonds et surfaces

| Rôle | Valeur | Usage |
|---|---|---|
| `background` | `#08080C` | Fond général, presque noir mais légèrement bleuté |
| `surface` | `#111119` | Cartes, feuilles modales |
| `surfaceVariant` | `#1A1A26` | Éléments imbriqués, champs |
| `outline` | `#2A2A3A` | Séparateurs |
| `onSurface` | `#EDEDF5` | Texte principal |
| `onSurfaceVariant` | `#9A9AB0` | Texte secondaire |

Le noir pur `#000000` est écarté : il crée des bords durs autour des lueurs et provoque du smearing sur les dalles OLED lors du défilement. `#08080C` conserve l'économie d'énergie OLED sans les artefacts.

### Macros

Le cœur du système. Ces six valeurs sont la seule source de vérité chromatique.

| Macro | Teinte | Hex | Raison |
|---|---|---|---|
| **Calories** | Cyan | `#00E5FF` | La mesure principale mérite la teinte la plus lumineuse |
| **Protéines** | Magenta | `#FF2D95` | Contraste maximal avec le cyan |
| **Glucides** | Violet | `#9D4EDD` | |
| **Sucres** | Violet clair | `#D9A5FF` | **Dérivé** des glucides : les sucres en sont un sous-ensemble, la parenté doit se voir |
| **Lipides** | Ambre | `#FFB020` | Seule teinte chaude, se détache immédiatement |
| **Fibres** | Vert | `#39FF88` | |

Le lien visuel sucres/glucides n'est pas un détail esthétique : c'est ce qui fait comprendre sans légende que la barre des sucres est une sous-graduation de celle des glucides.

Chaque teinte a trois déclinaisons dérivées par programme, jamais écrites à la main :

```
base    →  la valeur du tableau
glow    →  base à 35 % d'opacité, pour la lueur portée
muted   →  base désaturée de 60 %, pour l'état non atteint
```

### Contraste

Les six teintes dépassent 7:1 sur `#08080C` (niveau AAA pour du texte normal). Elles sont donc utilisables pour du texte, et pas seulement pour des tracés.

**Interdit** : texte foncé sur aplat néon. La combinaison est illisible en extérieur et ruine l'esthétique. Le néon est toujours l'élément *clair* de la paire.

### Daltonisme

Magenta et violet sont proches en deutéranopie, cyan et vert aussi en protanopie. La règle qui résout le problème sans dénaturer la palette :

> **La couleur ne porte jamais seule une information.** Chaque jauge, chaque segment, chaque légende porte un libellé ou une icône.

Les anneaux du calendrier — trop petits pour un libellé — respectent un **ordre angulaire fixe** (calories, protéines, glucides, lipides, fibres, sucres, dans le sens horaire depuis midi). La position devient le second canal d'information.

### Thème clair

Fourni, mais secondaire — cette application est pensée pour le sombre. Le thème clair inverse les fonds (`#FAFAFC` / `#FFFFFF`) et assombrit les macros de 25 % pour préserver le contraste sur fond blanc. **Aucune lueur** en thème clair : un halo sur fond clair ressemble à un défaut d'affichage.

---

## Typographie

Une seule famille : **Inter**, variable, embarquée (aucun appel réseau à un service de polices).

| Style | Taille / graisse | Usage |
|---|---|---|
| `display` | 48 / 700 | Le grand chiffre de calories restantes |
| `headline` | 28 / 600 | Titres d'écran |
| `title` | 20 / 600 | Titres de repas |
| `body` | 16 / 400 | Texte courant |
| `label` | 14 / 500 | Étiquettes de jauge |
| `caption` | 12 / 400 | Sources, dates, mentions |

**Chiffres tabulaires obligatoires** (`font-feature-settings: "tnum"`) sur tout compteur. Sans ça, un total qui passe de 1 199 à 1 200 fait sauter la mise en page — un défaut discret mais qui donne une impression de bâclé à chaque saisie.

---

## Composants

### `MacroRing`

L'anneau de calories. Diamètre 180 dp sur l'accueil, 44 dp dans le calendrier, 28 dp en vue mensuelle.

- Piste : `outline`, 8 dp.
- Progression : dégradé de `base` vers `base` éclairci de 20 %, extrémités arrondies.
- Lueur : `glow`, flou 16 dp, opacité proportionnelle à l'avancement — l'anneau s'allume à mesure qu'on approche de l'objectif. C'est la seule récompense visuelle de l'application, et elle suffit.
- Dépassement : un second arc se superpose, teinte `base` saturée, épaisseur 4 dp.
- Centre : chiffre restant en `display`, libellé en `caption`.

### `MacroBar`

Barre horizontale, hauteur 6 dp, coins arrondis. Étiquette à gauche, `consommé / objectif` à droite.

Deux modes, et la distinction est fonctionnelle :

- **Cible** (protéines, glucides, lipides, fibres) : la barre se remplit, la lueur croît avec l'avancement.
- **Plafond** (sucres) : la barre reste éteinte tant qu'on est sous le seuil, avec un repère marquant le plafond. Elle ne s'allume qu'au dépassement.

Cette différence traduit visuellement une différence réelle ([03](03-nutrition-calculs.md#sucres)) : atteindre ses protéines est un objectif, atteindre son plafond de sucres n'en est pas un.

### `DayPill`

Pastille du bandeau calendrier. 44 dp, `MacroRing` segmenté en couronne, jour de la semaine au-dessus, numéro au centre.

États : sélectionné (contour cyan + lueur), aujourd'hui (numéro en cyan), journalisé (anneaux colorés), non journalisé (anneau `outline` uniquement, sans remplissage — surtout pas un anneau à zéro), futur (opacité 40 %).

### `EntryRow`

Ligne du journal. Nom, quantité, calories, pastille de source. Balayage vers la gauche pour supprimer, avec un fond magenta qui se révèle progressivement.

La pastille de source est une icône monochrome de 16 dp, pas une couleur — les six couleurs sont réservées aux macros, et les diluer ailleurs casserait le système.

### `NeonButton`

Fond transparent, bordure 1,5 dp en `base`, texte en `base`, lueur externe au repos. À l'appui : fond à 12 % d'opacité, lueur intensifiée, réduction d'échelle à 0,97.

Le bouton principal de chaque écran est le seul à porter un fond plein : un dégradé vertical de la teinte **à faible opacité**, posé sur le fond sombre. Pas un aplat néon — il imposerait du texte foncé par-dessus, ce que la règle de contraste interdit. Le néon reste le trait et le texte ; le fond ne fait que le porter.

Un écran, un bouton plein : cette règle empêche l'inflation visuelle.

### `SourceBadge`

Étiquette de provenance sur l'écran de validation. Toutes les sources sont **neutres** : fond `surfaceVariant`, texte `onSurfaceVariant`, contour `outline`.

`Estimation IA` se distingue par la **forme**, jamais par la teinte : contour en pointillés, et un glyphe en vague de 16 dp devant le libellé. La distinction reste délibérée — une estimation ne se lit pas comme une donnée mesurée — mais elle ne coûte pas une couleur.

Trois raisons de ne pas lui en donner une :

1. Les six teintes portent un sens et un seul. En introduire une septième pour un badge, c'est commencer à diluer le système à l'endroit précis où on avait décidé de ne pas le faire.
2. La règle de daltonisme interdit qu'une couleur porte seule une information. Un badge coloré aurait de toute façon eu besoin d'un second canal : autant n'avoir que celui-là.
3. Un contour discontinu dit « valeur approximative » sans légende, dans les deux thèmes, et quel que soit le rendu des couleurs de l'écran.

---

## Espacement et formes

Grille de 4 dp. Marge d'écran 16 dp. Espace entre cartes 12 dp. Padding interne 16 dp.

Rayons : 8 dp (champs, badges), 16 dp (cartes), 24 dp (feuilles modales), plein (pastilles, boutons).

Cible tactile minimale : 48 × 48 dp, y compris sur les pastilles du calendrier qui font 44 dp visuellement — la zone tactile déborde le visuel.

---

## Animation

Le néon invite à trop animer. Cadre strict :

| Transition | Durée | Courbe |
|---|---|---|
| Valeur d'une jauge | 400 ms | `FastOutSlowIn` |
| Ouverture de feuille modale | 300 ms | `EmphasizedDecelerate` |
| Appui sur un bouton | 100 ms | `LinearOutSlowIn` |
| Apparition de contenu | 200 ms + décalage de 30 ms par élément | `FastOutSlowIn` |

**Aucune animation en boucle**, à une exception : l'attente d'analyse IA, où un flux néon parcourt le contour du cadre. C'est le seul moment où l'utilisateur attend plusieurs secondes sans rien faire, et où l'animation a une fonction — indiquer que le système travaille.

Le reste du temps, une lueur qui pulse en permanence consomme de la batterie et fatigue.

**Accessibilité** : quand `Settings.Global.ANIMATOR_DURATION_SCALE` vaut 0, ou quand la bascule « animations réduites » des réglages est active, toutes les durées tombent à 0 et l'animation d'attente devient un indicateur statique. Ce n'est pas optionnel : ce réglage existe souvent pour des raisons vestibulaires.

---

## Accessibilité

- **Contraste** : AA minimum partout, AAA sur les compteurs principaux.
- **TalkBack** : chaque jauge annonce une phrase utile — « Protéines, 87 grammes sur 144, 60 % » — et non « barre de progression, 60 % ».
- **Ordre de lecture** : chiffres du jour, puis repas dans l'ordre chronologique.
- **Taille de police** : la mise en page tient jusqu'à 200 % (`sp` partout pour le texte, aucune hauteur fixe sur un conteneur de texte).
- **Groupement** : une ligne de journal est un seul nœud d'accessibilité, avec ses actions personnalisées (modifier, supprimer) — plutôt que six nœuds à traverser.
- **Icônes seules** : toutes ont une `contentDescription`. Les décoratives sont explicitement marquées `null` pour ne pas polluer la lecture.

---

## Ressources

Trois fichiers dans `:core:designsystem`, et rien d'autre ne définit de couleur ou de durée :

```
MacroColors.kt      les six teintes et leurs dérivations
NeonTheme.kt        schéma Material 3, typographie, formes
Motion.kt           durées et courbes
```

Une valeur codée en dur dans un `:feature` est un défaut à corriger, pas un raccourci acceptable. C'est vérifié par une règle detekt personnalisée ([10](10-qualite-et-livraison.md)).

---

## Aperçus

Chaque composant expose un `@Preview` en thème sombre et en thème clair, plus un aperçu avec police à 200 % pour les composants qui affichent du texte. Ces aperçus servent aussi de base aux tests d'image, qui figent le rendu et détectent toute régression visuelle.
