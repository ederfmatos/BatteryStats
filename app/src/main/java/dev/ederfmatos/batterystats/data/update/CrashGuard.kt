package dev.ederfmatos.batterystats.data.update

import android.content.Context
import androidx.core.content.edit

/**
 * Rede de segurança contra uma atualização que não sobe.
 *
 * Conta arranques desde a última troca de versão e só zera quando o app chega inteiro à tela
 * inicial. Se ele morrer antes disso duas vezes seguidas, a versão nova está quebrada e a tela de
 * recuperação assume.
 *
 * Usa SharedPreferences, não DataStore: isto precisa ser lido e escrito de forma síncrona no
 * `onCreate` da Application, antes de qualquer corrotina existir — se o app crashar durante a
 * inicialização, uma escrita assíncrona não teria chegado ao disco.
 */
class CrashGuard(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Chamado no início do onCreate. Devolve true se esta versão já falhou vezes demais. */
    fun onAppStart(currentVersionCode: Long): Boolean {
        val recordedVersion = prefs.getLong(KEY_VERSION, -1L)
        if (recordedVersion != currentVersionCode) {
            prefs.edit {
                putLong(KEY_VERSION, currentVersionCode)
                putLong(KEY_PREVIOUS_VERSION, recordedVersion.takeIf { it > 0 } ?: -1L)
                putInt(KEY_UNCONFIRMED_STARTS, 1)
            }
            return false
        }

        val unconfirmed = prefs.getInt(KEY_UNCONFIRMED_STARTS, 0) + 1
        prefs.edit { putInt(KEY_UNCONFIRMED_STARTS, unconfirmed) }
        return unconfirmed > MAX_UNCONFIRMED_STARTS
    }

    /** Chamado quando a UI subiu de verdade. A partir daqui a versão é considerada boa. */
    fun confirmHealthy() {
        prefs.edit { putInt(KEY_UNCONFIRMED_STARTS, 0) }
    }

    fun previousVersionCode(): Long = prefs.getLong(KEY_PREVIOUS_VERSION, -1L)

    private companion object {
        const val PREFS_NAME = "crash-guard"
        const val KEY_VERSION = "version_code"
        const val KEY_PREVIOUS_VERSION = "previous_version_code"
        const val KEY_UNCONFIRMED_STARTS = "unconfirmed_starts"

        /** Dois arranques sem confirmação: a versão nova não chega à tela. */
        const val MAX_UNCONFIRMED_STARTS = 2
    }
}
