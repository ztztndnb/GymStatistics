package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymstatistics.data.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

private val portionSaver = Saver<List<NutritionPortion>, String>(
    save = { AppJson.json.encodeToString(it) },
    restore = { runCatching { AppJson.json.decodeFromString<List<NutritionPortion>>(it) }.getOrDefault(emptyList()) },
)

internal enum class NutritionBrand(val label: String) {
    MCDONALDS("麦当劳"), LAOXIANGJI("老乡鸡"), LAWSON("罗森")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NutritionScreenHeader(current: NutritionBrand, onBack: () -> Unit, onSelect: (NutritionBrand) -> Unit, onSource: () -> Unit) {
    Column {
        TopAppBar(title = { Text("营养库", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            actions = { TextButton(onClick = onSource) { Text("来源") } })
        PrimaryTabRow(selectedTabIndex = current.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            NutritionBrand.entries.forEach { brand -> Tab(selected = brand == current, onClick = { onSelect(brand) }, text = { Text(brand.label) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NutritionLibraryScreen(onBack: () -> Unit) {
    GymDesignTheme {
    var brand by rememberSaveable { mutableStateOf(NutritionBrand.MCDONALDS) }
    val stateHolder = rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(brand.name) {
        if (brand == NutritionBrand.MCDONALDS) McDonaldsNutritionScreen(onBack) { brand = it }
        else WeightNutritionScreen(brand, onBack) { brand = it }
    }
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McDonaldsNutritionScreen(onBack: () -> Unit, onSelectBrand: (NutritionBrand) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val repository = remember(context) { NutritionCatalogRepository(context) }
    val favoritesRepository = remember(context) { NutritionFavoritesRepository(context) }
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<Result<Pair<NutritionCatalog, Set<String>>>?>(null, attempt) {
        value = null
        value = try { Result.success(repository.load() to favoritesRepository.load()) } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
    var showSource by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { NutritionScreenHeader(NutritionBrand.MCDONALDS, onBack, onSelectBrand) { showSource = true } },
    ) { padding ->
        val catalog = result?.getOrNull()?.first
        if (catalog != null) {
            NutritionCatalogContent(catalog, result!!.getOrThrow().second, favoritesRepository, Modifier.padding(padding).consumeWindowInsets(padding))
            if (showSource) NutritionSourceDialog(catalog) { showSource = false }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (result == null) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在读取本地营养库…")
                } else {
                    Text("营养库加载失败", style = MaterialTheme.typography.titleMedium)
                    Text("本地餐品数据不可用，请重试或更新应用。无需配置 API Key。", Modifier.padding(vertical = 12.dp))
                    OutlinedButton(onClick = { attempt++ }) { Text("重试") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionCatalogContent(catalog: NutritionCatalog, initialFavorites: Set<String>, favoritesRepository: NutritionFavoritesRepository, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(initialFavorites) }
    var favoriteBusy by remember { mutableStateOf(false) }
    var favoriteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var portions by rememberSaveable(stateSaver = portionSaver) { mutableStateOf(emptyList<NutritionPortion>()) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    // Saved UI state may refer to a previous bundled snapshot after app restoration.
    fun currentPortions() = portions.filter { portion ->
        portion.quantity.isFinite() && portion.quantity > 0 && portion.quantity <= NutritionCalculator.MAX_QUANTITY &&
            catalog.products.any { it.id == portion.productId && it.variants.any { v -> v.id == portion.variantId } }
    }.distinctBy { it.productId to it.variantId }
    val validPortions = currentPortions()
    val totals = remember(catalog, validPortions) { NutritionCalculator.total(catalog, validPortions) }
    val category = catalog.categories.firstOrNull { it.id == categoryId }
    val needle = query.trim()
    val products = catalog.products.filter { product ->
        (category == null || product.id in category.productIds) && (!favoritesOnly || product.id in favorites) && product.name.contains(needle, ignoreCase = true)
    }
    fun change(productId: String, variantId: String, update: (Double) -> Double) {
        val latest = currentPortions()
        val current = latest.firstOrNull { it.productId == productId && it.variantId == variantId }?.quantity ?: 0.0
        val quantity = update(current).coerceIn(0.0, NutritionCalculator.MAX_QUANTITY)
        portions = NutritionCalculator.setQuantity(latest, productId, variantId, quantity)
    }
    Column(modifier.fillMaxSize()) {
        NutritionSearchField(query, { query = it }, "搜索餐品")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(category == null && !favoritesOnly, { categoryId = null; favoritesOnly = false }, label = { Text("全部") })
            FilterChip(favoritesOnly, { categoryId = null; favoritesOnly = true }, label = { Text("收藏") }, leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) })
            catalog.categories.forEach { entry -> FilterChip(!favoritesOnly && categoryId == entry.id, { categoryId = entry.id; favoritesOnly = false }, label = { Text(entry.name) }) }
        }
        Text("官方每份数据（更新于 ${catalog.sourceUpdated}）", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        favoriteError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (products.isEmpty()) item { Text(if (favoritesOnly && favorites.none { id -> catalog.products.any { it.id == id } }) "暂无收藏的餐品" else "没有匹配的餐品", Modifier.padding(16.dp)) }
            items(products, key = { it.id }) { product ->
                NutritionProductCard(product, product.id in favorites, !favoriteBusy, onFavorite = {
                    if (!favoriteBusy) {
                        val next = if (product.id in favorites) favorites - product.id else favorites + product.id
                        favoriteBusy = true
                        favoriteError = null
                        scope.launch {
                            try {
                                if (favoritesRepository.save(next)) favorites = next else favoriteError = "收藏保存失败，请重试"
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                favoriteError = "收藏保存失败，请重试"
                            } finally { favoriteBusy = false }
                        }
                    }
                }) { variant, quantity ->
                    change(product.id, variant.id) { it + quantity }
                }
            }
        }
        NutritionCartBar(
            if (totals.itemCount == 0) "尚未选择餐品" else "已选 ${totals.itemCount} 项，${nutritionNumber(totals.portions)} 份",
            "≈${nutritionNumber(totals.nutrition.energyKcal, true)} kcal", totals.itemCount > 0, { showCart = true },
        )
    }

    if (showCart) {
        ModalBottomSheet(onDismissRequest = { showCart = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("本次配餐", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { portions = emptyList(); showCart = false }) { Text("清空") }
                }
                NutritionTotalsGrid(totals.nutrition)
                Text("按官方份量计算，可按半份增减；未知值不按零计算。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(validPortions, key = { "${it.productId}:${it.variantId}" }) { portion ->
                        val product = catalog.products.first { it.id == portion.productId }
                        val variant = product.variants.first { it.id == portion.variantId }
                        val label = "${product.name}（${variant.name}）"
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NutritionProductImage(product, 44.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("${product.name}（${variant.name}）", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    IconButton(onClick = { change(product.id, variant.id) { 0.0 } }) { Icon(Icons.Rounded.Delete, "移除$label") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("每份 ≈${nutritionNumber(variant.perServing.energyKcal, true)} kcal", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    IconButton(onClick = { change(product.id, variant.id) { it - 0.5 } }) { Icon(Icons.Rounded.Remove, "减少${label}份数") }
                                    Text("${nutritionNumber(portion.quantity)} 份")
                                    IconButton(onClick = { change(product.id, variant.id) { it + 0.5 } }, enabled = portion.quantity < NutritionCalculator.MAX_QUANTITY) { Icon(Icons.Rounded.Add, "增加${label}份数") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionProductCard(product: NutritionProduct, favorite: Boolean, favoriteEnabled: Boolean, onFavorite: () -> Unit, onAdd: (NutritionVariant, Double) -> Unit) {
    var selectedId by rememberSaveable(product.id) { mutableStateOf(product.variants.first().id) }
    var editing by rememberSaveable(product.id) { mutableStateOf(false) }
    var quantityText by rememberSaveable(product.id) { mutableStateOf("1") }
    var expanded by remember { mutableStateOf(false) }
    val variant = product.variants.firstOrNull { it.id == selectedId } ?: product.variants.first()
    NutritionItemRow(product.name, if (product.variants.size > 1) "多种规格可选" else variant.name,
        "≈${nutritionNumber(variant.perServing.energyKcal, true)} kcal / 份", favorite, favoriteEnabled, onFavorite,
        onAdd = { editing = true }, image = { NutritionProductImage(product, 72.dp) }, addDescription = "添加${product.name}")
    if (editing) ModalBottomSheet(onDismissRequest = { editing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(product.name, style = MaterialTheme.typography.headlineSmall)
            Box {
                TextButton(onClick = { expanded = true }, enabled = product.variants.size > 1) {
                    Text(variant.name)
                    if (product.variants.size > 1) Icon(Icons.Rounded.ExpandMore, "选择${product.name}规格")
                }
                DropdownMenu(expanded, { expanded = false }) {
                    product.variants.forEach { entry -> DropdownMenuItem(text = { Text(entry.name) }, onClick = { selectedId = entry.id; expanded = false }) }
                }
            }
            val quantity = quantityText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= NutritionCalculator.MAX_QUANTITY }
            TextField(quantityText, { quantityText = it }, Modifier.fillMaxWidth(), label = { Text("食用份数") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), isError = quantity == null,
                supportingText = { if (quantity == null) Text("请输入大于0且不超过99的份数") })
            val amount = quantity ?: 0.0
            val nutrition = variant.perServing.let { n -> ServingNutrition(n.energyKj?.times(amount), n.proteinG?.times(amount), n.fatG?.times(amount), n.carbohydrateG?.times(amount), n.sodiumMg?.times(amount), n.calciumMg?.times(amount)) }
            Text("≈${nutritionNumber(nutrition.energyKcal, true)} kcal", style = MaterialTheme.typography.headlineMedium)
            NutritionTotalsGrid(nutrition)
            val note = variant.note.ifBlank { product.note }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { if (quantity != null) { onAdd(variant, quantity); editing = false } }, enabled = quantity != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("加入配餐") }
        }
    }
}

@Composable
private fun NutritionTotalsGrid(nutrition: ServingNutrition) {
    val values = with(nutrition) { listOf(
        "能量" to "${nutritionNumber(energyKcal, true)} kcal", "能量" to "${nutritionNumber(energyKj)} kJ",
        "蛋白质" to "${nutritionNumber(proteinG)} g", "脂肪" to "${nutritionNumber(fatG)} g",
        "碳水化合物" to "${nutritionNumber(carbohydrateG)} g", "钠" to "${nutritionNumber(sodiumMg)} mg", "钙" to "${nutritionNumber(calciumMg)} mg",
    ) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, value) ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NutritionSourceDialog(catalog: NutritionCatalog, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val date = Instant.parse(catalog.collectedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("数据来源与说明") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("来源：麦当劳中国官网营养计算器")
            Text("食品图片来自官网同一餐品的图片记录，已打包到本地，离线可用。")
            Text("官方营养数据更新：${catalog.sourceUpdated}\n本地采集日期：$date\n${catalog.products.size} 款餐品，${catalog.products.sumOf { it.variants.size }} 种规格")
            Text(catalog.disclaimer)
            Text("全部数值按官方每份口径；kcal 由 kJ ÷ 4.184 换算。未核实的字段不作为克重，不转换为每 100g。当前配餐不写入训练记录或食物分析历史。")
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        dismissButton = { TextButton(onClick = { uriHandler.openUri(catalog.sourceUrl) }) { Text("查看官网") } },
    )
}

private fun nutritionNumber(value: Double?, whole: Boolean = false): String = value?.let {
    DecimalFormat(if (whole) "0" else "0.##", DecimalFormatSymbols(Locale.US)).format(it)
} ?: "未知"
