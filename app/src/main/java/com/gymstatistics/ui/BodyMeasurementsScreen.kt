package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(
            title = { Text("身体数据") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
            actions = { IconButton(onClick = { showMetrics = true }) { Icon(Icons.Default.Settings, "管理身体指标") } },
        ) },
        bottomBar = {
            Button(
                onClick = { onEdit(LocalDate.now()) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
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
        ) {
            item {
                BodyChangesCard(body, LocalDate.now())
                HorizontalDivider(Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Text("测量历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            if (body.records.isEmpty()) item { Text("暂无身体数据记录，点击下方按钮添加。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(body.records.sortedByDescending { it.date }, key = { it.date }) { record ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(LocalDate.parse(record.date)) }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(record.date, style = MaterialTheme.typography.titleSmall)
                        Text(bodySummary(body, record), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (showMetrics) BodyMetricManager(model) { showMetrics = false }
}
