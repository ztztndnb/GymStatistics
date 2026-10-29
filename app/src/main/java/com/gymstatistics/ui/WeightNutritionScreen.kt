package com.gymstatistics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymstatistics.data.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeightNutritionScreen(brand: NutritionBrand, onBack: () -> Unit, onSelectBrand: (NutritionBrand) -> Unit) {
    val lawson = brand == NutritionBrand.LAWSON
    val sourceUrl = if (lawson) "https://www.fatsecret.cn/热量营养/罗森" else "https://github.com/lxjchina/lxj-trace/blob/main/references/report.pdf"
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val favoritesRepository = remember(context) { NutritionFavoritesRepository(context) }
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<Result<Pair<WeightNutritionCatalog, Set<String>>>?>(null, attempt, brand) {
        value = null
        value = try {
            val catalog = withContext(Dispatchers.IO) {
                context.assets.open(if (lawson) "nutrition/lawson-cn.json" else "nutrition/laoxiangji-cn.json").bufferedReader(Charsets.UTF_8).use {
                    AppJson.json.decodeFromString<WeightNutritionCatalog>(it.readText()).validated(lawson)
                }
            }
            Result.success(catalog to favoritesRepository.load())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
    var showSource by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("${brand.label}营养库", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        }, actions = {
            NutritionBrandButtons(brand, onSelectBrand)
            TextButton(onClick = { showSource = true }) { Text("来源") }
        })
    }) { padding ->
        val data = result?.getOrNull()
        if (data != null) {
            WeightNutritionContent(data.first, data.second, favoritesRepository, lawson, Modifier.padding(padding).consumeWindowInsets(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (result == null) CircularProgressIndicator() else {
                    Text("营养库加载失败")
                    OutlinedButton(onClick = { attempt++ }) { Text("重试") }
                }
            }
        }
    }
    if (showSource) AlertDialog(onDismissRequest = { showSource = false }, title = { Text("数据来源") }, text = {
        Text(if (lawson) "FatSecret 中国站第三方搜索摘要，整理于2026年10月29日，共250款。未核验商品包装、地区配方及当前在售情况，分类为按名称初步整理。按每100克显示，部分由来源每份克重换算；钠未知，不按零计算。" else "老乡鸡菜品溯源报告 2.0（2026年4月30日快照）。数据按每100克整理，未逐项与报告原文核对，实际菜品及数值可能变化；未知数据不按零计算。")
    }, confirmButton = { TextButton(onClick = { uriHandler.openUri(sourceUrl) }) { Text(if (lawson) "查看来源" else "查看报告") } }, dismissButton = { TextButton(onClick = { showSource = false }) { Text("关闭") } })
}

private fun weightNutritionNumber(value: Double?): String = value?.let { DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)).format(it) } ?: "未知"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightNutritionContent(catalog: WeightNutritionCatalog, initialFavorites: Set<String>, repository: NutritionFavoritesRepository, lawson: Boolean, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(initialFavorites) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf(hashMapOf<String, Double>()) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val duplicateNames = remember(catalog) { catalog.products.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys }
    val products = catalog.products.filter { it.name.contains(query.trim(), ignoreCase = true) && (category == null || it.category == category) && (!favoritesOnly || it.id in favorites) }
    val portions = selected.mapNotNull { (id, grams) -> catalog.products.firstOrNull { it.id == id }?.takeIf { grams.isFinite() && grams > 0 && grams <= 100_000 }?.let { WeightNutritionPortion(it, grams) } }
    val totals = weightNutritionTotals(portions)
    fun toggleFavorite(food: WeightNutritionFood) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                // Reload to retain favorites changed in another brand screen.
                val current = repository.load()
                val next = if (food.id in current) current - food.id else current + food.id
                if (repository.save(next)) favorites = next else error = "收藏保存失败，请重试"
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = "收藏保存失败，请重试"
            } finally { busy = false }
        }
    }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), label = { Text("搜索菜品") }, singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(category == null && !favoritesOnly, { category = null; favoritesOnly = false }, label = { Text("全部") })
            FilterChip(favoritesOnly, { category = null; favoritesOnly = true }, label = { Text("收藏") })
            catalog.products.map { it.category }.distinct().forEach { name -> FilterChip(category == name && !favoritesOnly, { category = name; favoritesOnly = false }, label = { Text(name) }) }
        }
        Text(if (lawson) "第三方资料，未核验包装。按每100克显示，请输入实际食用重量" else "报告数据按每100克显示，请输入实际食用重量", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (products.isEmpty()) item { Text("没有匹配的菜品", Modifier.padding(16.dp)) }
            items(products, key = { it.id }) { food ->
                var gramsText by rememberSaveable(food.id) { mutableStateOf("") }
                val grams = gramsText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100_000 }
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NutritionProductImage(food.id, food.name, if (lawson) "nutrition/lawson-images" else "nutrition/laoxiangji-images", 64.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(food.name, style = MaterialTheme.typography.titleMedium)
                                if (food.name in duplicateNames) Text("报告序号 ${food.sourceRow}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { toggleFavorite(food) }, enabled = !busy) { Icon(if (food.id in favorites) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (food.id in favorites) "取消收藏${food.name}" else "收藏${food.name}") }
                        }
                        Text("每100克：${weightNutritionNumber(food.energyKcal)} kcal，蛋白质 ${weightNutritionNumber(food.proteinG)} g，脂肪 ${weightNutritionNumber(food.fatG)} g，碳水 ${weightNutritionNumber(food.carbohydrateG)} g，钠 ${weightNutritionNumber(food.sodiumMg)} mg", style = MaterialTheme.typography.bodySmall)
                        if (lawson) TextButton(onClick = { food.sourceUrl?.let { uriHandler.openUri(it) } }) { Text("查看该商品来源") }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(gramsText, { gramsText = it }, Modifier.weight(1f), label = { Text("食用重量（克）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = gramsText.isNotEmpty() && grams == null)
                            Button(onClick = { if (grams != null) selected = HashMap(selected).apply { put(food.id, grams) } }, enabled = grams != null) { Text("加入配餐") }
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (portions.isEmpty()) "尚未选择菜品" else "已选 ${portions.size} 项，${weightNutritionNumber(portions.sumOf { it.grams })} 克", style = MaterialTheme.typography.titleSmall)
                Text("${weightNutritionNumber(totals.energyKcal)} kcal", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("蛋白质 ${weightNutritionNumber(totals.proteinG)} g，脂肪 ${weightNutritionNumber(totals.fatG)} g，碳水 ${weightNutritionNumber(totals.carbohydrateG)} g", style = MaterialTheme.typography.bodySmall)
                Text("钠 ${weightNutritionNumber(totals.sodiumMg)} mg", style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = { showCart = true }, enabled = portions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("查看已选") }
            }
        }
    }
    if (showCart) ModalBottomSheet(onDismissRequest = { showCart = false }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("本次配餐", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { selected = hashMapOf(); showCart = false }) { Text("清空") }
            }
            Text("按输入的重量计算，缺失的营养数据不按零计算。", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(portions, key = { it.food.id }) { portion ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${portion.food.name}${if (portion.food.name in duplicateNames) "（报告序号 ${portion.food.sourceRow}）" else ""}，${weightNutritionNumber(portion.grams)} 克", Modifier.weight(1f))
                        IconButton(onClick = { selected = HashMap(selected).apply { remove(portion.food.id) } }) { Icon(Icons.Rounded.Delete, "移除${portion.food.name}") }
                    }
                }
            }
        }
    }
}
