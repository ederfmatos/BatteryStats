package dev.ederfmatos.batterystats.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.domain.drain.RegimeStats

/**
 * Tela ligada contra tela desligada, lado a lado.
 *
 * O achado mais forte da coleta real foi que 76% do consumo aconteceu com a tela ligada. Isso é
 * uma **razão entre dois números**, e razão se lê comparando — não em duas linhas empilhadas de
 * rótulo à esquerda e valor à direita, que era como estava.
 */
@Composable
fun DrainComparison(
    screenOn: RegimeStats,
    screenOff: RegimeStats,
    modifier: Modifier = Modifier,
) {
    val maxMilliAmps = maxOf(
        screenOn.rangeHighMilliAmps,
        screenOff.rangeHighMilliAmps,
        1.0,
    )
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val errorBarColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RegimeColumn(
            label = stringResource(R.string.drain_screen_on),
            regime = screenOn,
            maxMilliAmps = maxMilliAmps,
            barColor = barColor,
            trackColor = trackColor,
            errorBarColor = errorBarColor,
            modifier = Modifier.weight(1f),
        )
        RegimeColumn(
            label = stringResource(R.string.drain_screen_off),
            regime = screenOff,
            maxMilliAmps = maxMilliAmps,
            barColor = barColor,
            trackColor = trackColor,
            errorBarColor = errorBarColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RegimeColumn(
    label: String,
    regime: RegimeStats,
    maxMilliAmps: Double,
    barColor: Color,
    trackColor: Color,
    errorBarColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (regime.isCoarse) {
                stringResource(
                    R.string.drain_range,
                    regime.rangeLowMilliAmps,
                    regime.rangeHighMilliAmps,
                )
            } else {
                stringResource(R.string.unit_milliamps, regime.averageMilliAmps)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.drain_percent_per_hour, regime.percentPerHour),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MagnitudeBar(
            regime = regime,
            maxMilliAmps = maxMilliAmps,
            barColor = barColor,
            trackColor = trackColor,
            errorBarColor = errorBarColor,
        )
    }
}

/**
 * Barra proporcional com a faixa de incerteza desenhada por cima.
 *
 * Uma faixa em texto ("40–290 mA") é honesta mas não dá noção de grandeza. Desenhada, dá para
 * *ver* que ela é larga demais para concluir alguma coisa — que é a informação que importa.
 */
@Composable
private fun MagnitudeBar(
    regime: RegimeStats,
    maxMilliAmps: Double,
    barColor: Color,
    trackColor: Color,
    errorBarColor: Color,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(10.dp)
    ) {
        val trackHeight = 6f
        val top = (size.height - trackHeight) / 2f
        drawRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
        )

        val fraction = (regime.averageMilliAmps / maxMilliAmps).coerceIn(0.0, 1.0).toFloat()
        drawRect(
            color = barColor,
            topLeft = Offset(0f, top),
            size = Size(size.width * fraction, trackHeight),
        )

        if (regime.isCoarse && regime.uncertaintyMilliAmps > 0.0) {
            val low = (regime.rangeLowMilliAmps / maxMilliAmps).coerceIn(0.0, 1.0).toFloat()
            val high = (regime.rangeHighMilliAmps / maxMilliAmps).coerceIn(0.0, 1.0).toFloat()
            val y = size.height / 2f
            drawLine(
                color = errorBarColor,
                start = Offset(size.width * low, y),
                end = Offset(size.width * high, y),
                strokeWidth = 2f,
            )
            listOf(low, high).forEach { edge ->
                drawLine(
                    color = errorBarColor,
                    start = Offset(size.width * edge, y - 4f),
                    end = Offset(size.width * edge, y + 4f),
                    strokeWidth = 2f,
                )
            }
        }
    }
}
