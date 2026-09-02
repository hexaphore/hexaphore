package app.hexavore.domain.appearance

import kotlinx.coroutines.flow.Flow

/**
 * Le thème que l'utilisateur a choisi.
 *
 * **[SYSTEM] est un choix, pas une absence de choix.** C'est l'écart avec le jour
 * regardé, où `null` veut dire aujourd'hui : là, la valeur nulle protégeait d'un écran
 * qui resterait sur la veille après minuit. Ici, « suivre le système » est une intention
 * durable, et la ranger comme un vide obligerait chaque lecteur à savoir ce que le vide
 * signifie.
 */
enum class ThemeMode {
    LIGHT,
    DARK,

    /** Ce que l'application faisait avant qu'on puisse choisir, et le défaut. */
    SYSTEM,
    ;

    /**
     * Faut-il peindre sombre ?
     *
     * **Une fonction pure plutôt qu'un `when` dans la composition.** Le réglage
     * d'Android arrive en paramètre : la règle s'éprouve alors sans écran, et il n'y a
     * qu'un endroit qui sache ce que « suivre le système » veut dire.
     */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemIsDark
    }
}

/**
 * Ce que l'utilisateur a réglé sur l'apparence de l'application.
 *
 * **Une préférence d'appareil, et elle ne voyage pas.** [D96][decisions] a fixé que la
 * sauvegarde ne porte que ce que la base tient ; un thème vit dans les préférences, et
 * restaurer un export sur un autre téléphone n'a aucune raison d'y imposer le thème du
 * premier. Le système d'unités, lui, est une propriété du profil et voyage avec lui.
 *
 * **Son propre fichier de préférences.** Effacer ses clés d'IA remet à zéro les réglages
 * d'IA ; l'apparence n'a rien à voir avec elles et n'a pas à disparaître avec.
 *
 * [decisions]: docs/11-decisions.md
 * @see docs/02-parcours-et-ecrans.md
 */
interface AppearanceSettings {
    fun observe(): Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
