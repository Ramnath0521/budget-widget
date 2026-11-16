package io.github.swashbuckler104.budgetwidget.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetTheme
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup

/**
 * A category's mark: its icon in its color on a deep tile of the same hue, edged in that color
 * (ADR-0052). Decorative: the name always sits next to it.
 */
@Composable
fun CategoryAvatar(emoji: String, colorArgb: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = BudgetTheme.categoryColor(colorArgb)
    val shape = RoundedCornerShape(percent = TILE_CORNER_PERCENT)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(BudgetTheme.categoryContainer(colorArgb))
            .border(1.dp, color.copy(alpha = TILE_EDGE_ALPHA), shape)
            .clearAndSetSemantics { },
    ) { CategoryGlyph(emoji, color, size * GLYPH_SHARE) }
}

/** The icon for [emoji] tinted [tint], or the emoji itself when it has no icon. [size] is the icon's box. */
@Composable
fun CategoryGlyph(emoji: String, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val icon = CategoryIcons.forEmoji(emoji)
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
    } else {
        // Fixed size (dp, not sp): the tile doesn't grow with the font scale, so neither does this.
        val fontSize = with(LocalDensity.current) { (size * EMOJI_SHARE).toSp() }
        Text(emoji, fontSize = fontSize, maxLines = 1, modifier = modifier.clearAndSetSemantics { })
    }
}

private const val TILE_CORNER_PERCENT = 32
private const val TILE_EDGE_ALPHA = 0.45f
private const val GLYPH_SHARE = 0.55f
private const val EMOJI_SHARE = 0.85f

/** A one-tap category choice: emoji plus name. At least 48 dp tall through the chip's touch target. */
@Composable
fun CategoryChip(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            CategoryGlyph(category.emoji, BudgetTheme.categoryColor(category.colorArgb), FilterChipDefaults.IconSize)
        },
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    )
}

/**
 * Every pickable leaf, grouped under its top-level category (SCOPE.md F4 "full category grid with
 * subcategories"). Groups with nothing to pick are left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    groups: List<CategoryGroup>,
    onPick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.filter { it.pickableLeaves.isNotEmpty() }.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryAvatar(group.category.emoji, group.category.colorArgb, size = 24.dp)
                    Text(
                        group.category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.pickableLeaves.forEach { leaf ->
                        CategoryChip(leaf, onClick = { onPick(leaf) }, selected = leaf.id == selectedId)
                    }
                }
            }
        }
    }
}
