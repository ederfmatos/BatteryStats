package dev.ederfmatos.batterystats.domain.drain

/** Mediana de uma lista. Devolve null para lista vazia em vez de estourar. */
internal fun List<Double>.medianOrNull(): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}
