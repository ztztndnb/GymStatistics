package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gymstatistics.GymViewModel
import com.gymstatistics.data.BodyData
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMeasurementsScreen(
    model: GymViewModel,
    onEdit: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val body = model.uiState.data.bodyData ?: BodyData()
    var showMetrics by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { TopAppBar(
            title = { Text("身体数据") },
            actions = { IconButton(onClick = { showMetrics = true }) { Icon(Icons.Default.Settings, "管理身体指标") } },
        ) },
        bottomBar = {
            Button(
                onClick = { onEdit(LocalDate.now()) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加记录")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("body-dashboard"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { BodyChangesCard(body, LocalDate.now()) }
            item { Text("测量历史", style = MaterialTheme.typography.titleMedium) }
            if (body.records.isEmpty()) item { Text("暂无身体数据记录，点击下方按钮添加。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(body.records.sortedByDescending { it.date }, key = { it.date }) { record ->
                Card(
                    onClick = { onEdit(LocalDate.parse(record.date)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(record.date, style = MaterialTheme.typography.titleSmall)
                        Text(bodySummary(body, record), style = MaterialTheme.typography.bodyMedium)
                        Text("点击编辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showMetrics) BodyMetricManager(model) { showMetrics = false }
}
