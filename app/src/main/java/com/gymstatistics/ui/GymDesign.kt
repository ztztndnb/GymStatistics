package com.gymstatistics.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun GymDesignTheme(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFCDB2ED), onPrimary = Color(0xFF261733), primaryContainer = Color(0xFF382A46),
        background = Color(0xFF171A18), surface = Color(0xFF171A18), onSurface = Color(0xFFF1F3F0),
        onSurfaceVariant = Color(0xFFBDC5BD), surfaceContainerLow = Color(0xFF202522),
        surfaceContainerHigh = Color(0xFF2A302C), outline = Color(0xFF9AA59C), outlineVariant = Color(0xFF363D37),
    ) else lightColorScheme(
        primary = Color(0xFF7553AA), onPrimary = Color.White, primaryContainer = Color(0xFFF1ECF8), onPrimaryContainer = Color(0xFF65438D),
        background = Color.White, surface = Color.White, onSurface = Color(0xFF202321),
        onSurfaceVariant = Color(0xFF69706B), surfaceContainerLow = Color(0xFFF8F9F7),
        surfaceContainerHigh = Color(0xFFF0F2EF), outline = Color(0xFF727B73), outlineVariant = Color(0xFFE9ECE8),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun NutritionSearchField(value: String, onValueChange: (String) -> Unit, label: String) {
    TextField(value, onValueChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ))
}

@Composable
internal fun NutritionItemRow(
    name: String, subtitle: String, energy: String, favorite: Boolean, favoriteEnabled: Boolean,
    onFavorite: () -> Unit, onAdd: () -> Unit, image: @Composable () -> Unit,
    addDescription: String = "添加$name", extra: @Composable () -> Unit = {},
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            image()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onFavorite, enabled = favoriteEnabled, modifier = Modifier.size(48.dp)) {
                        Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            (if (favorite) "取消收藏" else "收藏") + name, Modifier.size(20.dp),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(energy, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Icon(Icons.Rounded.Add, addDescription, Modifier.size(20.dp))
                    }
                }
                extra()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun NutritionCartBar(summary: String, energy: String, enabled: Boolean, onOpen: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.bodySmall)
                Text(energy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onOpen, enabled = enabled, colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) { Text("查看已选") }
        }
    }
}
