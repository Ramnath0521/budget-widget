package io.github.swashbuckler104.budgetwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryIcons
import kotlin.math.roundToInt

/**
 * The app's category icons for widgets (ADR-0054). Glance can only show bitmaps and resources, so
 * an icon's vector paths are drawn once into a small white bitmap, which the widget then tints.
 */
internal object CategoryIconBitmaps {
    private val cache = HashMap<String, Bitmap>()

    /** The icon for [emoji] as a white [PIXELS]-square bitmap, or null when it has no icon. */
    fun forEmoji(emoji: String): Bitmap? {
        val icon = CategoryIcons.forEmoji(emoji) ?: return null
        return synchronized(cache) { cache.getOrPut(icon.name) { render(icon) } }
    }

    private fun render(icon: ImageVector): Bitmap {
        val bitmap = createBitmap(PIXELS, PIXELS)
        val canvas = Canvas(bitmap)
        canvas.scale(PIXELS / icon.viewportWidth, PIXELS / icon.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        draw(icon.root, canvas, paint)
        return bitmap
    }

    // Material icons are plain filled paths in untransformed groups, which is all this handles.
    private fun draw(group: VectorGroup, canvas: Canvas, paint: Paint) {
        for (node in group) {
            when (node) {
                is VectorGroup -> draw(node, canvas, paint)

                is VectorPath -> {
                    paint.alpha = (node.fillAlpha * OPAQUE).roundToInt()
                    canvas.drawPath(PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(), paint)
                }
            }
        }
    }

    private const val PIXELS = 96
    private const val OPAQUE = 255
}

/** A category's icon tinted [tint], or its emoji as text when it has no icon. */
@Composable
internal fun CategoryMark(emoji: String, tint: ColorProvider, size: Dp, emojiSize: TextUnit) {
    val bitmap = CategoryIconBitmaps.forEmoji(emoji)
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(size),
        )
    } else {
        Text(emoji, maxLines = 1, style = TextStyle(fontSize = emojiSize))
    }
}
