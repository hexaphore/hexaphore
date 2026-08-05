# 02 — Parcours et écrans

Carte de navigation, puis chaque écran en détail : ce qu'il montre, ce qu'on peut y faire, et ce qui se passe quand ça se passe mal.

## Carte

```
Onboarding (première ouverture uniquement)
   └─> Accueil ─┬─> Réglages ─┬─> Profil et objectifs
                │             ├─> Fournisseurs d'IA
                │             ├─> Sauvegarde
                │             ├─> Apparence
                │             └─> À propos
                ├─> Journée (jour passé sélectionné dans le calendrier)
                ├─> Calendrier étendu (vue mensuelle)
                ├─> Journal de poids
                └─> [FAB] ─┬─> Scan code-barres      ┐
                           ├─> Photo                 │ 4 modales
                           ├─> Recherche             │ qui convergent
                           └─> Texte libre           ┘ vers ↓
                                                Validation d'entrée
```

Une seule règle structurante : **les quatre modes de saisie se rejoignent sur le même écran de validation**. Un seul composant à concevoir, à tester et à corriger, et un geste identique quel que soit le chemin emprunté.

---

## Onboarding

Cinq étapes, une question par écran, barre de progression en haut. Bouton « Passer » sur toutes les étapes sauf la première : un utilisateur pressé doit pouvoir arriver à l'accueil. Les champs sautés prennent une valeur par défaut raisonnable et sont signalés dans les réglages.

**1. Accueil.** Nom de l'application, une phrase, un bouton. Affiche l'avertissement : *« Hexaphore est un outil de suivi personnel. Ce n'est pas un dispositif médical et il ne remplace pas l'avis d'un professionnel de santé. »* — à accepter pour continuer.

**2. Vous.** Date de naissance (sélecteur), sexe, taille, poids actuel.

Le sexe propose **Homme / Femme / Je préfère ne pas répondre**. La formule de Mifflin-St Jeor n'a que deux variantes ; la troisième option applique la moyenne des deux, et l'écran le dit : *« Nous utiliserons une estimation intermédiaire, que vous pourrez ajuster ensuite. »* Cacher ce détail produirait un chiffre inexplicable.

Unités : kg et cm par défaut, lb et ft/in disponibles dans les réglages, conversion à l'affichage uniquement — **le stockage est toujours métrique** (voir [07](07-modele-de-donnees.md)).

**3. Activité.** Cinq niveaux, chacun décrit par un exemple concret plutôt que par un adjectif. « Modérément actif » ne veut rien dire ; « sport 3 à 5 fois par semaine » se répond en une seconde.

**4. Objectif.** Trois cartes : *Perdre du poids* · *Maintenir* · *Prendre du poids*. Puis poids cible (curseur pré-positionné sur une valeur plausible) et échéance (+3 mois / +6 mois / +12 mois / date libre).

Un aperçu se met à jour en direct sous les curseurs : « ≈ 0,6 kg par semaine ». Si le rythme sort des bornes de sécurité ([03](03-nutrition-calculs.md#garde-fous)), l'écran ne bloque pas : il affiche la date atteignable la plus proche et propose de s'y caler en un tap.

**5. Vos objectifs.** Les six chiffres calculés, chacun avec une phrase d'explication en une ligne. Bouton « Ajuster » vers l'édition manuelle, bouton « C'est parti » vers l'accueil.

---

## Accueil

L'écran par défaut, celui qu'on ouvre dix fois par jour. Il tient en un défilement vertical.

### Bandeau calendrier (fixe en haut)

Sept pastilles de jour, défilement horizontal vers le passé (chargement paginé, pas de limite), le jour courant à droite et sélectionné par défaut.

Chaque pastille porte le jour de la semaine, le numéro, et un **anneau segmenté** reprenant les six couleurs de macro. Un segment se remplit à mesure que l'objectif du jour est atteint ; il passe en mode « dépassement » (trait plus épais, teinte saturée) au-delà. La couleur des sucres, qui est un plafond et non une cible, s'allume seulement en dépassement.

Le jour de départ d'un objectif porte un liseré : on voit où une nouvelle phase a commencé.

Tap sur une pastille → écran **Journée**. Tap sur l'en-tête du mois → **Calendrier étendu**.

### Bloc « Reste aujourd'hui »

Le cœur de l'écran. Un grand anneau de calories au centre, cinq barres de macro dessous.

Le chiffre affiché est le **restant**, pas le consommé — c'est l'information dont on a besoin au moment de décider quoi manger. Le consommé et l'objectif sont écrits en dessous, plus petits.

En cas de dépassement, le chiffre devient négatif et l'anneau se poursuit en surcouche d'une teinte plus vive. Aucun message moralisateur, aucun rouge d'alerte : c'est une donnée, pas un jugement.

Les sucres se lisent différemment : leur barre est une sous-graduation à l'intérieur de celle des glucides, en violet clair, avec un repère au niveau du plafond.

### Bloc du jour

En tête d'écran, l'**hexagone des macros** : six quartiers, un par compteur, remplis depuis le centre, le contour marquant l'objectif du jour ([08](08-design-system.md#macrohexagon)). C'est la figure qui donne son nom à l'application, et elle répond à une seule question — comment va ma journée.

Sous l'hexagone, le **restant en calories** en grand chiffre, puis les **six barres** dans le même ordre angulaire que les quartiers. Elles répondent à l'autre question : combien exactement. L'hexagone ne peut dire ni « 87 / 144 g », ni le `max` d'une limite, ni le `≥` d'un total minoré.

### Liste des plats

Les plats de la journée, du plus ancien au plus récent. Un **plat** est ce qu'on a saisi en une fois : plusieurs aliments, une seule origine.

Pas de petit-déjeuner, de déjeuner ni de dîner. Ces catégories obligeraient à ranger chaque saisie dans une case avant de l'enregistrer, pour répondre à une question qu'on ne se pose pas : ce qui compte est ce qu'on a mangé aujourd'hui, et l'heure situe déjà chaque plat ([D31](11-decisions.md)).

**En tête de plat** : sa pastille d'origine, son heure, son total de calories.

**En pied de plat** : ses cinq autres apports. Un plat qui ne se lit que par son énergie ne dit pas d'où viennent les protéines ni ce qui a fait grimper les sucres — or c'est exactement la question qu'on se pose en relisant sa journée.

Chaque ligne d'aliment montre nom, quantité, calories. **Pas de pastille par ligne** : la source appartient au plat ([D32](11-decisions.md)).

- **Tap** → ouvre l'écran de validation de cette ligne, en édition.
- **Balayage vers la gauche** → supprimer, avec `Snackbar` d'annulation (5 s). Aucune suppression n'est immédiatement définitive.
- **Appui long** → menu : dupliquer, déplacer vers un autre repas, enregistrer comme favori.

### Bouton d'ajout

Bouton d'action flottant en bas à droite. Un tap déploie quatre actions étiquetées, en arc :

| | Action | Ouvre |
|---|---|---|
| ⌗ | Scanner | Modale code-barres |
| ⛶ | Photographier | Modale photo |
| ⌕ | Rechercher | Modale recherche |
| ✎ | Décrire | Modale texte |

L'ordre est fixe. Un ordre adaptatif « selon vos habitudes » ferait bouger les cibles sous le doigt et détruirait la mémoire musculaire — c'est exactement le contraire du but.

---

## Modale : scan de code-barres

Feuille modale plein écran, aperçu caméra, cadre de visée, torche en haut à droite.

Décodage continu par ML Kit sur le flux CameraX. Formats acceptés : EAN-13, EAN-8, UPC-A, UPC-E — les formats de produits alimentaires, rien d'autre, pour éviter les faux positifs sur les QR codes.

**Anti-rebond** : un code n'est retenu qu'après deux lectures identiques consécutives, puis le scan se met en pause et le téléphone vibre brièvement. Sans cela, la caméra enchaîne les détections et l'écran clignote.

Séquence après lecture :

1. Recherche dans le catalogue local → si trouvé, affichage immédiat, aucun réseau.
2. Sinon, appel Open Food Facts avec un état de chargement inline (pas de dialogue bloquant).
3. Fiche trouvée → écran de validation, pré-rempli avec la portion de l'emballage si l'information existe, sinon 100 g.

**Produit introuvable.** Le cas est fréquent et doit rester agréable. L'écran affiche le code lu et trois issues : *Créer cet aliment* (formulaire pré-rempli avec le code-barres, la fiche est enregistrée en local et réutilisable indéfiniment), *Chercher par nom* (bascule vers la recherche), *Annuler*.

**Sans réseau.** Message explicite, code-barres mémorisé, proposition de créer l'aliment à la main. Le code reste associé : au prochain scan connecté, l'app proposera de compléter depuis Open Food Facts.

**Permission caméra refusée.** Explication de l'usage et bouton vers les réglages système. Les trois autres modes restent disponibles.

---

## Modale : photo

1. **Prise de vue.** Aperçu CameraX, déclencheur, bascule galerie. Conseil discret en surimpression : *« Cadrez l'assiette entière, de préférence vue de dessus. »*
2. **Contexte facultatif.** Un champ d'une ligne : « Un détail à préciser ? (facultatif) » — par exemple *« l'assiette fait 24 cm »* ou *« la sauce est allégée »*. Ce texte est joint au prompt. C'est le levier de précision le moins coûteux qui existe.
3. **Analyse.** L'image est réduite (1024 px sur le côté long, JPEG qualité 80) puis envoyée au fournisseur configuré. Écran d'attente avec animation néon et bouton **Annuler** qui coupe réellement la requête.
4. **Validation.** Écran de validation multi-lignes.

Le fichier temporaire vit dans le cache de l'application et est supprimé dans un bloc `finally`, que l'appel réussisse, échoue ou soit annulé. Il n'entre jamais dans la galerie du téléphone.

**Aucune clé API configurée.** L'entrée « Photographier » reste visible mais grisée ; un tap ouvre une explication courte et un raccourci vers les réglages. La masquer laisserait croire que la fonctionnalité n'existe pas.

**Échec.** Les erreurs sont traduites en langage humain, jamais en code HTTP : clé invalide → « Votre clé pour *Gemini* a été refusée. Vérifiez-la dans les réglages. » ; quota → « Votre fournisseur a refusé la requête : quota atteint. » ; réseau → « Pas de connexion. » Dans tous les cas, la photo est conservée en mémoire le temps de proposer **Réessayer**, et une porte de sortie vers la saisie manuelle est offerte.

---

## Modale : recherche

Feuille modale qui monte aux deux tiers, champ de recherche focalisé et clavier ouvert immédiatement.

**À l'ouverture, avant toute frappe** — c'est l'écran le plus utilisé de l'application :

- **Récents** : les 20 derniers aliments distincts, tous modes de saisie confondus, triés par date de dernière utilisation.
- **Favoris** : aliments et repas composés épinglés, en tête.

**Pendant la frappe** : résultats à partir du **2ᵉ caractère**, une fois écoulées **120 ms sans nouvelle frappe**. La requête n'est jamais lancée à chaque touche : on attend que la saisie se stabilise, et une frappe qui arrive avant l'échéance annule la précédente. Sans cela, taper « chocolat » déclenche sept recherches dont six sont jetées, et les résultats clignotent pendant qu'on écrit.

Recherche locale sur trois sources fusionnées et ordonnées :

1. aliments personnels et produits déjà scannés (ce que l'utilisateur mange vraiment),
2. CIQUAL,
3. suggestion « Chercher *« … »* dans Open Food Facts » en dernière ligne, si le réseau est disponible.

Accents et casse ignorés (« creme brulee » trouve « crème brûlée »). Chaque résultat affiche nom, marque éventuelle, et calories pour 100 g — assez pour choisir sans ouvrir.

Tap → écran de validation avec la quantité par défaut de l'aliment (voir [04](04-sources-de-donnees.md#portions-usuelles)).

**Aucun résultat.** Bouton « Créer *« pâtes de mamie »* » qui ouvre le formulaire d'aliment personnel avec le nom pré-rempli.

---

## Modale : texte libre

Une zone de texte, un exemple en placeholder, un bouton « Analyser ».

> *deux œufs brouillés, une tranche de pain complet, un verre de jus d'orange*

L'analyse suit exactement le même pipeline que la photo — le contrat d'entrée du modèle accepte une image ou un texte, rien d'autre ne change ([05](05-ia.md)). Mêmes erreurs, mêmes messages, même écran de sortie.

La dernière saisie est conservée tant que la modale n'a pas abouti, pour qu'un échec réseau ne fasse jamais retaper une phrase.

---

## Écran de validation d'entrée

Le point de convergence. Une ou plusieurs lignes, chacune éditable, un bouton d'enregistrement.

Chaque ligne présente :

- **Nom de l'aliment**. Pas de source par ligne : elle appartient au plat, et le badge se pose une fois en tête d'écran ([D32](11-decisions.md)). À partir de la tranche 6, une ligne dont les chiffres sont une **estimation** plutôt qu'une correspondance porte un marqueur — l'utilisateur doit savoir quand un chiffre est une supposition, mais c'est une propriété de la valeur, pas une seconde source.
- **Quantité** : champ numérique + sélecteur d'unité (g, ml, et les portions nommées disponibles pour cet aliment : « 1 tranche », « 1 verre »). Les macros se recalculent en direct.
- **Confiance IA**, sur les lignes issues d'une analyse : une correspondance faible est visuellement signalée et propose jusqu'à 3 aliments alternatifs, sans obliger à choisir.
- **Macros dépliables** : les six valeurs, chacune éditable. Une valeur modifiée à la main est marquée et ne sera plus jamais recalculée automatiquement pour cette ligne.
- **Date** : aujourd'hui par défaut, ou la date consultée si on vient d'un jour passé. Il n'y a **pas** de repas de destination à choisir : les lignes de cet écran forment un plat, et le plat se range tout seul à son heure.

En bas : total de la saisie, et son impact sur les compteurs du jour (« il vous restera 780 kcal »). Actions : **Enregistrer**, **Ajouter une ligne**, **Supprimer une ligne** (balayage), **Enregistrer comme plat favori**.

Cet écran est aussi celui qu'on obtient en tapant sur une ligne déjà enregistrée : même composant, en mode édition. Les macros affichées sont alors celles **figées à l'enregistrement**, pas celles recalculées depuis la source — un produit reformulé par son fabricant ne doit pas réécrire le passé.

---

## Écran Journée

Ouvert depuis le calendrier. Structurellement identique à l'accueil, avec trois différences :

- La date est affichée en titre, avec des flèches jour précédent / jour suivant, et le balayage horizontal fonctionne aussi.
- Les objectifs comparés sont **ceux qui étaient actifs ce jour-là**, pas les objectifs actuels. Un objectif modifié aujourd'hui ne réécrit pas l'appréciation d'il y a deux mois.
- Un encart de synthèse en tête : écart à chaque objectif, en valeur et en pourcentage.

L'ajout et l'édition y sont pleinement disponibles : on rattrape un oubli de la veille comme on saisit le repas du jour.

---

## Calendrier étendu

Grille mensuelle, une case par jour, chaque case portant l'anneau segmenté en réduction. Défilement vertical entre les mois. En bas, trois indicateurs sur la période affichée : jours journalisés, moyenne calorique, écart moyen à l'objectif.

Une journée sans aucune saisie est visuellement neutre — et non « à zéro ». Confondre « je n'ai rien noté » avec « je n'ai rien mangé » fausserait toutes les moyennes, ici comme dans l'algorithme d'adaptation ([03](03-nutrition-calculs.md#adaptation-hebdomadaire)).

---

## Journal de poids

Liste des pesées et courbe. Deux tracés : les points bruts, discrets, et la **moyenne mobile sur 7 jours**, en évidence — le poids brut varie de deux kilos avec l'hydratation et décourage sans raison.

La trajectoire visée est superposée en pointillés, du départ de l'objectif jusqu'à la date cible.

Ajout d'une pesée par un bouton flottant : poids et date, c'est tout.

Si l'algorithme a une suggestion d'ajustement en attente, une carte apparaît en tête : *« Sur les 3 dernières semaines vous perdez 0,3 kg par semaine, pour 0,5 visé. Réduire l'objectif de 120 kcal ? »* — **Accepter** / **Ignorer** / **Ne plus proposer**. Rien n'est jamais appliqué sans accord.

---

## Réglages

Écran simple à sections.

**Profil et objectifs** — toutes les données de l'onboarding, modifiables. Bouton « Recalculer mes objectifs » et édition manuelle des six valeurs. Un objectif édité à la main est marqué comme tel et n'est plus écrasé par un recalcul sans confirmation explicite.

**Intelligence artificielle** — liste des fournisseurs. Pour chacun : clé API (masquée, avec bouton « Tester »), modèle, et pour le fournisseur générique, l'URL de base. Un fournisseur actif est désigné par défaut. En bas, compteur d'utilisation : appels et coût estimé par fournisseur, remise à zéro possible.

**Sauvegarde** — connexion Google Drive, dernière sauvegarde, bascule automatique, « Sauvegarder maintenant », « Restaurer », « Exporter dans un fichier », « Importer un fichier ».

**Apparence** — thème (sombre / clair / système), langue, unités (métrique / impérial), animations réduites.

**À propos** — version, lien du dépôt, licence, attributions CIQUAL et Open Food Facts, avertissement médical, lien de don *(variante hors Play Store uniquement, voir [10](10-qualite-et-livraison.md#variantes-de-build))*.

---

## Comportements transverses

**Restauration d'état.** Toute saisie en cours survit à une rotation, à un passage en arrière-plan et à la destruction du processus. Les états d'écran vivent dans un `ViewModel` avec `SavedStateHandle`.

**Erreurs.** Trois niveaux et pas un de plus : `Snackbar` pour le récupérable (« Supprimé »
+ Annuler), encart inline pour l'échec d'une action en cours (avec Réessayer), dialogue uniquement pour ce qui est destructif ou irréversible (restauration d'une sauvegarde, suppression d'un aliment utilisé dans l'historique).

**Chargements.** Squelettes plutôt que roues, sauf pour les appels IA où l'attente est longue et mérite une animation assumée. Aucun écran bloquant.

**Retour arrière.** Une modale se ferme sur retour. Une modale contenant une saisie non enregistrée demande confirmation avant de se fermer — une fois, sans insister.

**Premier lancement à vide.** Chaque liste vide dit quoi faire, pas seulement qu'elle est vide : « Aucun repas pour l'instant. Scannez un produit ou décrivez ce que vous avez mangé. »
