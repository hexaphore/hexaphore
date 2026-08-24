package app.hexavore.core.testing

import app.hexavore.domain.identity.IdGenerator

/**
 * Des identifiants prévisibles.
 *
 * `id-1`, `id-2`, `id-3`… C'est ce qui permet à un test d'affirmer *quel* plat a
 * été enregistré et *quelles* lignes lui ont été rattachées, et non seulement
 * qu'une écriture a eu lieu. Avec des UUID, la seule vérification possible serait
 * de relire la base et de faire confiance à ce qu'elle rend — c'est-à-dire de
 * tester l'écriture avec la lecture.
 *
 * @param prefix pour qu'un test qui construit deux générateurs distingue leurs
 *   sorties d'un coup d'œil dans un message d'échec.
 */
class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var count = 0

    override fun next(): String = "$prefix-${++count}"
}
