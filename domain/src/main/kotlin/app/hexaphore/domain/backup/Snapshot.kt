package app.hexaphore.domain.backup

import app.hexaphore.domain.diary.Dish
import app.hexaphore.domain.diary.FavoriteDish
import app.hexaphore.domain.food.Food
import app.hexaphore.domain.goal.AdjustmentSetup
import app.hexaphore.domain.goal.Goal
import app.hexaphore.domain.profile.UserProfile
import app.hexaphore.domain.profile.WeightEntry
import java.time.Instant

/**
 * La version du **format de fichier**, pas celle de l'application.
 *
 * Elle s'incrémente à chaque changement incompatible, et l'importeur applique une
 * chaîne de migrations `v1 → v2 → v3` ([docs/09][donnees]). Une sauvegarde de 2026 doit
 * rester lisible en 2029 : c'est le minimum qu'on doit à quelqu'un qui a noté ses repas
 * pendant trois ans.
 *
 * **Ajouter un champ facultatif ne l'incrémente pas** — un ancien fichier se relit sans
 * lui. Renommer, retirer, ou changer le sens d'un champ, oui.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
const val SNAPSHOT_FORMAT_VERSION = 1

/**
 * Tout ce que l'utilisateur a écrit, à un instant donné.
 *
 * **Ce qui n'y est pas est aussi important que ce qui y est.** Pas de clé d'API —
 * contrainte ferme de [docs/01][perimetre], et un test l'affirme sur les octets
 * produits. Pas de mot de passe Open Food Facts, pour la même raison. Pas de photos :
 * elles n'existent plus au moment de la sauvegarde. Pas de base CIQUAL : elle est dans
 * l'APK.
 *
 * **Pas d'identifiant d'appareil non plus**, que [docs/09][donnees] prévoyait. Rien ne
 * le lit, et un fichier que l'utilisateur peut envoyer par courriel n'a pas besoin de
 * porter de quoi relier deux exports au même téléphone.
 *
 * [foods] contient **tout le catalogue local**, y compris les fiches venues de l'ANSES.
 * [docs/09][donnees] prévoyait de n'en garder que le code, mais une fiche de l'ANSES
 * n'est copiée localement qu'à son premier usage et reçoit alors un identifiant propre
 * à l'installation : l'exclure romprait le lien que chaque ligne de journal tient vers
 * elle. Le filtrage qu'elle visait est déjà obtenu autrement — le catalogue local ne
 * contient que ce qui a servi.
 *
 * [perimetre]: docs/01-perimetre.md
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
data class Snapshot(
    val exportedAt: Instant,
    val appVersion: String,
    val profile: UserProfile? = null,
    val goals: List<Goal> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val dishes: List<Dish> = emptyList(),
    val foods: List<Food> = emptyList(),
    val favorites: List<FavoriteDish> = emptyList(),
    /**
     * L'état de l'adaptation hebdomadaire.
     *
     * Restauré avec le reste : quelqu'un qui vient d'accepter un ajustement, ou de
     * répondre « ne plus proposer », ne doit pas revoir la carte parce qu'il a changé
     * de téléphone. C'est une décision de l'utilisateur, pas un réglage d'appareil.
     */
    val adjustment: AdjustmentSetup = AdjustmentSetup(),
) {
    /** De quoi reconnaître un fichier sans l'ouvrir entièrement. */
    val entryCount: Int get() = dishes.sumOf { it.entries.size }
}
