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

Là où un libellé ne tient pas, c'est la **position** qui sert de second canal. L'ordre angulaire est donc unique dans toute l'application, et il est celui de l'hexagone :

> **calories, protéines, fibres, glucides, sucres, lipides — dans le sens horaire depuis le haut.**

Une figure qui adopterait un autre ordre annulerait tout le bénéfice : la position ne renseigne que si elle est la même partout. C'est cet ordre que suivent l'hexagone, les pastilles du calendrier et les barres de l'accueil.

### Thème clair

Fourni, mais secondaire — cette application est pensée pour le sombre. Le thème clair inverse les fonds (`#FAFAFC` / `#FFFFFF`) et assombrit les macros de 25 % pour préserver le contraste sur fond blanc. **Aucune lueur** en thème clair : un halo sur fond clair ressemble à un défaut d'affichage.

---

## Typographie

Une seule famille : **Inter**, variable, embarquée (aucun appel réseau à un service de polices).

| Style | Taille / graisse | Usage |
|---|---|---|
| `display` | 48 / 700 | Le grand chiffre de calories restantes |
| `headline` | 28 / 600 | Titres d'écran |
| `title` | 20 / 600 | Titres de section, en-têtes de plat |
| `body` | 16 / 400 | Texte courant |
| `label` | 14 / 500 | Étiquettes de jauge |
| `caption` | 12 / 400 | Sources, dates, mentions |

**Chiffres tabulaires obligatoires** (`font-feature-settings: "tnum"`) sur tout compteur. Sans ça, un total qui passe de 1 199 à 1 200 fait sauter la mise en page — un défaut discret mais qui donne une impression de bâclé à chaque saisie.

---

## Composants

### `MacroHexagon`

La figure principale de l'accueil, et celle qui donne son nom au projet : six macros, six quartiers.

#### Géométrie

Hexagone régulier **à sommet plat** — deux arêtes horizontales, en haut et en bas. Angles mesurés depuis l'est, sens antihoraire ; en coordonnées écran l'ordonnée descend, donc un point d'angle θ et de rayon r se trouve en `(cx + r·cos θ, cy − r·sin θ)`.

- **Sommets** à 0°, 60°, 120°, 180°, 240°, 300°.
- **Arêtes** centrées sur 30°, 90°, 150°, 210°, 270°, 330°.

Chaque macro occupe le quartier d'un axe, dans le **sens horaire depuis le haut** :

| Macro | Axe | Arête |
|---|---|---|
| Calories | 90° | haut |
| Protéines | 30° | haut-droite |
| Fibres | 330° | bas-droite |
| Glucides | 270° | bas |
| Sucres | 210° | bas-gauche |
| Lipides | 150° | haut-gauche |

Un quartier est le triangle `centre → sommet à (axe − 30°) → sommet à (axe + 30°)`. À 100 %, il coïncide exactement avec l'arête ; le contour de l'hexagone **est** l'objectif.

#### Remplissage

Le quartier d'une macro à un ratio *r* est le même triangle, homothétique de rapport *r* depuis le centre. Le remplissage part donc du milieu, sans trou central : six pointes qui convergent restent lisibles à 220 dp, et un anneau intérieur contredirait « en partant du milieu ».

**Le rayon est proportionnel à la valeur, pas la surface.** Un quartier à 50 % occupe donc le quart de l'aire de sa part. C'est la même convention que l'anneau et les barres — la longueur, pas l'aire — et l'incohérence serait de changer de règle d'un composant à l'autre. À écrire ici parce qu'un lecteur pressé conclura l'inverse.

#### Objectifs et limites

La distinction de [03](03-nutrition-calculs.md#objectifs-et-limites) vaut ici comme ailleurs, sans quoi un quartier de sucres bien rempli se lirait comme une réussite.

- **Objectif** — calories, protéines, fibres : quartier en teinte `base`, lueur croissante avec le remplissage.
- **Limite** — glucides, sucres, lipides : quartier en teinte `muted` tant que *r* ≤ 1. Au-delà, le quartier entier passe en `base` avec sa lueur, et la part qui sort du contour est saturée de 30 %.

Une journée bien tenue montre donc **trois quartiers vifs et trois quartiers sourds**. Ne pas allumer une limite, c'est déjà réussir.

#### Mise à l'échelle

Le contour de l'objectif n'est pas la limite du dessin : un quartier peut le dépasser. Pour que rien ne sorte de la zone allouée :

```
rPlafonné(m) = min(ratio(m), 2.0)
ajustement   = 1 / max(1, max des rPlafonné)
Rcible       = Rzone × ajustement
```

Quand rien ne dépasse, l'hexagone cible remplit la zone. Quand une macro atteint 200 %, la cible se réduit de moitié et le quartier débordant touche le bord. **Le rétrécissement de l'hexagone cible est lui-même le signal** : on voit qu'on a débordé avant même d'avoir lu quelle macro.

`Rzone = min(largeur / 2, hauteur / √3)` — un hexagone à sommet plat de circumrayon R mesure 2R de large et √3·R de haut.

**Plafond à 200 %.** Au-delà, le quartier s'arrête là et son arête extérieure est tracée **en dents de scie**, la convention de rupture d'échelle des graphiques. Sans ce plafond, une saisie erronée à 2 000 % réduirait l'hexagone cible à un point et rendrait toute la figure illisible pour corriger l'erreur — c'est-à-dire au pire moment.

#### Totaux minorés

Un quartier dont le total est amputé d'une valeur inconnue ([D29](11-decisions.md)) voit son arête extérieure **s'estomper** sur les six derniers dp, au lieu d'être nette. On ne sait pas où ça s'arrête, la figure ne prétend donc pas le savoir. Aucune légende n'est nécessaire ; la mention chiffrée reste sous les barres.

#### Repères

Le contour de l'objectif est tracé **par-dessus** les quartiers, en `outline`, 2 dp. Il ne doit jamais être masqué : c'est la référence à laquelle tout le reste se compare.

L'initiale de chaque macro est posée **à l'extérieur** du contour, au milieu de son arête, dans la teinte de la macro. Six lettres suffisent, tiennent à 200 % de police, et donnent le second canal exigé par la règle de daltonisme — la position en donne déjà un.

#### Accessibilité

L'hexagone est **exclu de l'arbre d'accessibilité**. Ce n'est pas un oubli : les mêmes six valeurs sont juste en dessous, dans les barres, sous une forme qui se lit bien mieux à la voix — une phrase par macro, avec son objectif et son pourcentage. Faire annoncer six clauses par une figure dupliquerait l'information et rallongerait la traversée de l'écran.

**Cette exclusion tient tant que les barres restent.** Si elles disparaissaient un jour, l'hexagone devrait reprendre l'annonce.

#### Animation

Le facteur d'ajustement et les six ratios s'animent ensemble, à la durée et à la courbe des jauges (400 ms, `FastOutSlowIn`). Ils tombent à zéro comme le reste quand l'appareil demande moins d'animations.

---

### `MacroRing`

L'anneau d'une macro. **Il n'est plus la figure de tête de l'accueil** — `MacroHexagon` l'a remplacé ([D33](11-decisions.md)) — mais il reste le composant des petites échelles : 44 dp dans le bandeau calendrier, 28 dp en vue mensuelle, et partout où une seule macro doit se lire d'un coup d'œil.

Diamètre de référence 180 dp.

- Piste : `outline`, 8 dp.
- Progression : dégradé de `base` vers `base` éclairci de 20 %, extrémités arrondies.
- Lueur : `glow`, flou 16 dp, opacité proportionnelle à l'avancement — l'anneau s'allume à mesure qu'on approche de l'objectif. C'est la seule récompense visuelle de l'application, et elle suffit.
- Dépassement : un second arc se superpose, teinte `base` saturée, épaisseur 4 dp.
- Centre : emplacement libre, laissé à l'appelant — le numéro du jour dans une pastille de calendrier.

### `MacroBar`

Barre horizontale, hauteur 6 dp, coins arrondis. Étiquette à gauche, `consommé / objectif` à droite.

Deux comportements, et la distinction est fonctionnelle. Elle n'est **pas** choisie par l'écran appelant : elle est portée par la macro elle-même ([03](03-nutrition-calculs.md#objectifs-et-limites)), pour qu'aucun écran ne puisse en décider autrement.

- **Objectif** — calories, protéines, fibres : la barre se remplit, la lueur croît avec l'avancement.
- **Limite** — glucides, sucres, lipides : la barre reste éteinte sous le seuil, avec un repère marquant la limite. Elle ne s'allume qu'au dépassement.

Trois signaux séparent les deux, et ils sont redondants à dessein — l'un d'eux suffit à lever le doute, quel que soit le canal disponible :

| | Objectif | Limite |
|---|---|---|
| Valeur affichée | `87 / 144 g` | `41 / 63 g max` |
| Jauge | se remplit, s'allume | éteinte sous le seuil, repère à la limite |
| TalkBack | « sur un objectif de 144 » | « sur une limite de 63 » |

Sans cela, une jauge de sucres se lit comme une jauge de protéines, et la remplir ressemble à une réussite alors que c'en est exactement le contraire. Ne pas allumer une limite, c'est déjà réussir.

### `DayPill`

Pastille du bandeau calendrier. 44 dp, `MacroRing` segmenté en couronne, jour de la semaine au-dessus, numéro au centre.

**Le calendrier garde l'anneau.** L'hexagone est réservé au récapitulatif d'une journée — accueil et écran Journée. À 44 dp, et plus encore à 28 dp en vue mensuelle, six quartiers ne se distingueraient plus les uns des autres ; un anneau segmenté reste lisible parce que ses segments sont concentriques et non adjacents.

États : sélectionné (contour cyan + lueur), aujourd'hui (numéro en cyan), journalisé (anneaux colorés), non journalisé (anneau `outline` uniquement, sans remplissage — surtout pas un anneau à zéro), futur (opacité 40 %).

### `EntryRow`

Ligne d'aliment dans un plat. Nom, quantité, calories. Balayage vers la gauche pour supprimer, avec un fond magenta qui se révèle progressivement.

**Aucune pastille de source ici** : elle appartient au plat et se pose une fois en tête ([D32](11-decisions.md)). Cinq pastilles voisines ne distinguaient plus rien.

### Apports d'un plat

Sous les lignes d'un plat, ses cinq autres apports : `P 52 g · G 61 g · S 4,8 g · L 7,9 g · F 1,4 g`.

L'initiale et la teinte portent la même information — une couleur ne renseigne jamais seule — et la teinte est celle de la macro, donc celle des barres du haut : la correspondance se fait sans légende.

Un total amputé d'une valeur inconnue s'écrit `F ≥ 1,4 g`. Le symbole n'est pas décoratif : il dit que la vraie quantité est supérieure, ce qu'une valeur nue laisserait croire exact.

### `NeonButton`

Fond transparent, bordure 1,5 dp en `base`, texte en `base`, lueur externe au repos. À l'appui : fond à 12 % d'opacité, lueur intensifiée, réduction d'échelle à 0,97.

**Trois disponibilités, dont deux façons d'être éteint.** La distinction n'est pas cosmétique — elle répond à un défaut constaté sur appareil : un bouton grisé qui ne bouge pas du tout ne se distingue pas d'une application figée.

| | Au repos | À l'appui | Pour quoi |
|---|---|---|---|
| **Disponible** | teinte pleine, lueur | échelle 0,97, lueur intensifiée | le cas courant |
| **Indisponible** | grisé, sans lueur | **réagit quand même**, puis explique | mode IA sans clé ([02](02-parcours-et-ecrans.md#modale--photo)) |
| **Désactivé** | grisé, sans lueur | rien, et TalkBack l'annonce désactivé | action déjà en cours |

Masquer un bouton indisponible laisserait croire que la fonctionnalité n'existe pas ; le rendre inerte laisse croire que l'appareil ne répond plus. Il reste donc visible, grisé, et répond à l'appui pour dire ce qui manque.

Le bouton principal de chaque écran est le seul à porter un fond plein : un dégradé vertical de la teinte **à faible opacité**, posé sur le fond sombre. Pas un aplat néon — il imposerait du texte foncé par-dessus, ce que la règle de contraste interdit. Le néon reste le trait et le texte ; le fond ne fait que le porter.

Un écran, un bouton plein : cette règle empêche l'inflation visuelle.

### `SourceBadge`

Étiquette d'origine d'un **plat** — un plat, une source, posée une fois en tête. Toutes les sources sont **neutres** : fond `surfaceVariant`, texte et contour `onSurfaceVariant`.

Un contenu **proposé** par un modèle — photo ou description — se distingue par la **forme**, jamais par la teinte : contour en pointillés, et un glyphe en vague de 16 dp devant le libellé. Une recherche, un code-barres ou une saisie manuelle portent un contour plein et discret. Le raisonnement qui écarte une septième couleur est en [D25](11-decisions.md).

**Les tirets se mesurent en dp, pas en pixels.** Six unités de tiret font deux millimètres sur une dalle à densité 1 et un quart de millimètre sur une dalle à densité 4 : à ce stade, le pointillé est un trait plein. C'est le défaut qui a rendu le badge indistinguable sur un téléphone récent. Le tracé est en outre encarté d'une demi-épaisseur, faute de quoi sa moitié extérieure sort des limites du composant et se fait rogner.

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
- **Ordre de lecture** : chiffres du jour, puis plats dans l'ordre chronologique.
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
