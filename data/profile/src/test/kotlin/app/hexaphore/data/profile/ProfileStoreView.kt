package app.hexaphore.data.profile

import app.hexaphore.domain.goal.Goals
import app.hexaphore.domain.profile.Profiles
import app.hexaphore.domain.profile.WeightLog

/**
 * Les trois ports du profil, réunis le temps d'un test.
 *
 * Le domaine les sépare exprès — l'onboarding écrit les trois d'un bloc, l'accueil ne
 * lit que [Goals] — et cette réunion ne remet pas ce choix en cause : elle vit dans le
 * jeu de sources de test et n'est visible d'aucun écran.
 *
 * Trois délégués et non un seul, contrairement à `FoodCatalogView` : côté Room, c'est
 * `RoomProfileStore` qui les tient tous les trois, mais côté mémoire ce sont trois
 * objets distincts. Le contrat n'a aucune raison d'imposer aux implémentations une
 * forme d'assemblage qui n'existe que pour lui.
 */
class ProfileStoreView(profiles: Profiles, weights: WeightLog, goals: Goals) :
    Profiles by profiles,
    WeightLog by weights,
    Goals by goals
