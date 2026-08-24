package app.hexavore.core.common.identity

import app.hexavore.domain.identity.IdGenerator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les identifiants réels : des UUIDv4.
 *
 * `java.util.UUID` et non un générateur maison : sa version 4 tire 122 bits
 * aléatoires d'un `SecureRandom`, ce qui rend une collision moins probable qu'une
 * panne de disque. Écrire mieux serait long et n'apporterait rien.
 *
 * Singleton pour ne pas reconstruire le générateur aléatoire à chaque ligne saisie.
 * Il n'a aucun état mutable propre, donc rien à protéger entre fils d'exécution.
 */
@Singleton
class UuidGenerator @Inject constructor() : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}
