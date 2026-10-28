package com.gymstatistics.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gymstatistics.data.NutritionProduct
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NutritionProductImage(product: NutritionProduct, size: Dp = 76.dp) {
    NutritionProductImage(product.id, product.name, "nutrition/images", size)
}

@Composable
internal fun NutritionProductImage(id: String, name: String, directory: String, size: Dp = 76.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, id, directory) {
        value = withContext(Dispatchers.IO) {
            try {
                val path = "$directory/$id.png"
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while (maxOf(options.outWidth, options.outHeight) / sample > 512) sample *= 2
                options.inJustDecodeBounds = false
                options.inSampleSize = sample
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            } catch (_: IOException) { null }
        }
    }
    Surface(Modifier.size(size), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, "${name}官方图片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                ?: Icon(Icons.Rounded.Fastfood, "${name}图片暂不可用", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
