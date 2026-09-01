package dev.ederfmatos.batterystats.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallStatusMapperTest {

    /**
     * Os valores são reproduzidos em InstallStatusMapper para manter a camada de domínio livre de
     * android.*. Este teste é o que impede alguém de trocar um número por engano — eles foram
     * conferidos contra o android.jar da plataforma 37.
     */
    @Test
    fun `constantes batem com as da plataforma`() {
        assertEquals(0, InstallStatusMapper.STATUS_SUCCESS)
        assertEquals(1, InstallStatusMapper.STATUS_FAILURE)
        assertEquals(2, InstallStatusMapper.STATUS_FAILURE_BLOCKED)
        assertEquals(3, InstallStatusMapper.STATUS_FAILURE_ABORTED)
        assertEquals(4, InstallStatusMapper.STATUS_FAILURE_INVALID)
        assertEquals(5, InstallStatusMapper.STATUS_FAILURE_CONFLICT)
        assertEquals(6, InstallStatusMapper.STATUS_FAILURE_STORAGE)
        assertEquals(7, InstallStatusMapper.STATUS_FAILURE_INCOMPATIBLE)
        assertEquals(8, InstallStatusMapper.STATUS_FAILURE_TIMEOUT)
        assertEquals(-1, InstallStatusMapper.STATUS_PENDING_USER_ACTION)
    }

    @Test
    fun `pedido de acao do usuario nao e falha`() {
        val outcome = InstallStatusMapper.map(InstallStatusMapper.STATUS_PENDING_USER_ACTION)

        assertEquals(InstallOutcome.NeedsUserAction, outcome)
    }

    @Test
    fun `cada status vira a falha correspondente`() {
        val casos = mapOf(
            InstallStatusMapper.STATUS_FAILURE_CONFLICT to InstallFailure.CONFLICT,
            InstallStatusMapper.STATUS_FAILURE_INCOMPATIBLE to InstallFailure.INCOMPATIBLE,
            InstallStatusMapper.STATUS_FAILURE_STORAGE to InstallFailure.STORAGE,
            InstallStatusMapper.STATUS_FAILURE_ABORTED to InstallFailure.ABORTED,
            InstallStatusMapper.STATUS_FAILURE_BLOCKED to InstallFailure.BLOCKED,
            InstallStatusMapper.STATUS_FAILURE_INVALID to InstallFailure.UNKNOWN,
            InstallStatusMapper.STATUS_FAILURE_TIMEOUT to InstallFailure.UNKNOWN,
        )

        casos.forEach { (status, expected) ->
            val outcome = InstallStatusMapper.map(status)
            assertEquals("status $status", expected, (outcome as InstallOutcome.Failed).failure)
        }
    }

    @Test
    fun `silencioso escala para o dialogo do sistema`() {
        assertEquals(
            InstallStep.SYSTEM_DIALOG,
            InstallStatusMapper.nextStepAfter(InstallStep.SILENT, InstallFailure.UNKNOWN),
        )
    }

    @Test
    fun `bloqueio cai direto no instalador do sistema`() {
        assertEquals(
            InstallStep.OPEN_APK,
            InstallStatusMapper.nextStepAfter(InstallStep.SILENT, InstallFailure.BLOCKED),
        )
    }

    @Test
    fun `download corrompido cai no link direto`() {
        assertEquals(
            InstallStep.DIRECT_LINK,
            InstallStatusMapper.nextStepAfter(InstallStep.SILENT, InstallFailure.CORRUPTED),
        )
    }

    @Test
    fun `assinatura divergente nao escala para lugar nenhum`() {
        // Nenhum instalador aceita chave diferente; oferecer outro degrau seria enganar o usuário.
        assertNull(InstallStatusMapper.nextStepAfter(InstallStep.SILENT, InstallFailure.CONFLICT))
        assertNull(
            InstallStatusMapper.nextStepAfter(InstallStep.OPEN_APK, InstallFailure.CONFLICT)
        )
    }

    @Test
    fun `cancelamento do usuario nao escala sozinho`() {
        assertNull(
            InstallStatusMapper.nextStepAfter(InstallStep.SYSTEM_DIALOG, InstallFailure.ABORTED)
        )
    }

    @Test
    fun `o link direto e o fim da cascata`() {
        assertNull(
            InstallStatusMapper.nextStepAfter(InstallStep.DIRECT_LINK, InstallFailure.UNKNOWN)
        )
    }
}
