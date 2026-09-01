package dev.ederfmatos.batterystats.ui.report

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.domain.report.BatteryReport
import dev.ederfmatos.batterystats.domain.report.ReportFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var lastReport: BatteryReport? = null

    init {
        generate()
    }

    fun generate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(generating = true, truncatedForLink = false)
            val report = runCatching { container.reportBuilder.build() }
                .onFailure { Log.e(TAG, "Falha ao montar o relatório", it) }
                .getOrNull()
            lastReport = report
            _uiState.value = _uiState.value.copy(
                markdown = report?.let { ReportFormatter.format(it) }.orEmpty(),
                generating = false,
            )
        }
    }

    fun setAttachRawJson(attach: Boolean) {
        _uiState.value = _uiState.value.copy(attachRawJson = attach)
    }

    /** Caminho principal: sempre funciona. */
    fun shareIntent(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            val markdown = _uiState.value.markdown
            if (markdown.isEmpty()) return@launch
            val rawFile = if (_uiState.value.attachRawJson) {
                val samples = container.statsRepository.snapshotsSince(
                    System.currentTimeMillis() - SEVEN_DAYS_MS
                )
                container.reportSharer.writeRawJson(samples)
            } else {
                null
            }
            onIntent(container.reportSharer.shareIntent(markdown, rawFile))
        }
    }

    fun copy() {
        val markdown = _uiState.value.markdown
        if (markdown.isNotEmpty()) container.reportSharer.copyToClipboard(markdown)
    }

    /**
     * Caminho secundário. Se o relatório completo não couber no link, cai automaticamente na
     * versão curta e avisa na UI — nunca envia silenciosamente algo diferente do que está na tela.
     */
    fun claudeIntent(): Intent? {
        val markdown = _uiState.value.markdown
        if (markdown.isEmpty()) return null

        container.reportSharer.claudeChatIntent(markdown)?.let {
            _uiState.value = _uiState.value.copy(truncatedForLink = false)
            return it
        }

        val report = lastReport ?: return null
        val short = ReportFormatter.formatShort(report)
        val intent = container.reportSharer.claudeChatIntent(short)
        _uiState.value = _uiState.value.copy(truncatedForLink = intent != null)
        return intent
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReportViewModel::class.java)) {
                "Factory não sabe criar ${modelClass.name}"
            }
            return ReportViewModel(application) as T
        }
    }

    private companion object {
        const val TAG = "ReportViewModel"
        const val SEVEN_DAYS_MS = 7 * 86_400_000L
    }
}
