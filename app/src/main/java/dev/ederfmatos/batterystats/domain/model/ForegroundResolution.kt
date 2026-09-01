package dev.ederfmatos.batterystats.domain.model

/**
 * Por que uma amostra não tem app em primeiro plano.
 *
 * Sem essa distinção, `foregroundPackage = null` significa três coisas diferentes ao mesmo tempo e
 * não dá para saber se o buraco no ranking é falta de permissão, tela apagada ou serviço morto.
 * Numa coleta real, 22% do consumo ficou sem app atribuído por não haver como distinguir.
 */
enum class ForegroundReason {
    /** A permissão de acesso ao uso não está concedida. O ranking inteiro fica degradado. */
    NO_PERMISSION,

    /** Houve um buraco de amostragem: a timeline de primeiro plano tem uma lacuna real. */
    GAP,

    /** A tela estava apagada. Não existe app em primeiro plano no sentido que interessa. */
    SCREEN_OFF,
}

/** O app em primeiro plano numa amostra, ou o motivo de não haver um. */
data class ForegroundResolution(
    val packageName: String?,
    val reason: ForegroundReason?,
) {
    init {
        require(packageName == null || reason == null) {
            "Uma resolução tem pacote ou motivo, nunca os dois"
        }
    }

    companion object {
        fun of(packageName: String) = ForegroundResolution(packageName, null)
        fun absent(reason: ForegroundReason) = ForegroundResolution(null, reason)
    }
}
