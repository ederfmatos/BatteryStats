package dev.ederfmatos.batterystats.data.battery

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot

/**
 * Fonte de leituras da bateria. Existe como interface para que a UI e os testes possam trocar por
 * um fake sem depender de um device.
 */
interface BatteryReader {
    /** Lê o estado atual. Retorna null se o sistema não devolver o broadcast sticky da bateria. */
    fun read(): BatterySnapshot?
}
