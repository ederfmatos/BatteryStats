package dev.ederfmatos.batterystats.ui.common

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Uma linha rótulo/valor.
 *
 * Substitui as três implementações duplicadas de `Row` com `SpaceBetween` que existiam em
 * HomeScreen, DiagnosticsScreen e CollectionHealthScreen. Nenhuma delas usava `weight`, então com
 * fonte grande o rótulo empurrava o valor para fora da tela. `ListItem` já resolve distribuição de
 * largura, altura mínima de toque e os papéis de cor certos.
 */
@Composable
fun LabeledRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
