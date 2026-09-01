package dev.ederfmatos.batterystats.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.domain.drain.HourlyDrain
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot

/**
 * Gráficos em Canvas puro.
 *
 * O que estas duas telas precisam — faixas sombreadas de tela ligada por cima da linha de nível e
 * marcadores de início de carga — é específico o bastante para que qualquer biblioteca genérica de
 * charts custasse mais em configuração do que estas ~120 linhas de desenho, além de outra
 * dependência para manter em dia.
 */
@Composable
fun LevelOverTimeChart(
    samples: List<BatterySnapshot>,
    lineColor: Color,
    screenOnColor: Color,
    chargeMarkerColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2) return
    val ordered = samples.sortedBy { it.timestampMs }
    val startMs = ordered.first().timestampMs
    val endMs = ordered.last().timestampMs
    val spanMs = (endMs - startMs).coerceAtLeast(1L)

    Box(modifier.fillMaxWidth().height(180.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            fun x(timestampMs: Long) = size.width * (timestampMs - startMs) / spanMs
            fun y(levelPct: Int) = size.height * (1f - levelPct.coerceIn(0, 100) / 100f)

            // Faixas de tela ligada, desenhadas por baixo da linha.
            var runStart: Long? = null
            for (index in ordered.indices) {
                val sample = ordered[index]
                if (sample.screenOn && runStart == null) runStart = sample.timestampMs
                val isLast = index == ordered.lastIndex
                if ((!sample.screenOn || isLast) && runStart != null) {
                    val left = x(runStart)
                    val right = x(sample.timestampMs)
                    drawRect(
                        color = screenOnColor,
                        topLeft = Offset(left, 0f),
                        size = Size((right - left).coerceAtLeast(1f), size.height),
                    )
                    runStart = null
                }
            }

            // Linhas de referência em 0, 25, 50, 75 e 100%.
            for (level in 0..100 step 25) {
                val lineY = y(level)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 1f,
                )
            }

            val path = Path()
            ordered.forEachIndexed { index, sample ->
                val pointX = x(sample.timestampMs)
                val pointY = y(sample.levelPct)
                if (index == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

            // Marcadores nos instantes em que a carga começou.
            for (index in 1..ordered.lastIndex) {
                val previous = ordered[index - 1]
                val current = ordered[index]
                if (!previous.isCharging && current.isCharging) {
                    drawCircle(
                        color = chargeMarkerColor,
                        radius = 6f,
                        center = Offset(x(current.timestampMs), y(current.levelPct)),
                    )
                }
            }
        }
    }
}

/** Histograma de dreno por hora do dia. É aqui que um consumo noturno anômalo fica visível. */
@Composable
fun HourlyDrainChart(
    hourly: List<HourlyDrain>,
    barColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    if (hourly.isEmpty()) return
    val byHour = hourly.associateBy { it.hourOfDay }
    val maxMilliAmps = hourly.maxOf { it.averageMilliAmps }.coerceAtLeast(1.0)

    Box(modifier.fillMaxWidth().height(160.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slotWidth = size.width / 24f
            val barWidth = slotWidth * 0.7f

            drawLine(
                color = gridColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f,
            )

            for (hour in 0..23) {
                val drain = byHour[hour] ?: continue
                val barHeight =
                    (size.height * (drain.averageMilliAmps / maxMilliAmps)).toFloat()
                val left = hour * slotWidth + (slotWidth - barWidth) / 2f
                drawRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
    }
}
