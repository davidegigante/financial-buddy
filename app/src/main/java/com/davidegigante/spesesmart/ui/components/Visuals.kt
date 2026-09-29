package com.davidegigante.spesesmart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davidegigante.spesesmart.data.Category
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.ui.theme.AppTheme
import com.davidegigante.spesesmart.ui.theme.TABULAR

/** Card "pulita" dell'app: bianca su sfondo leggermente colorato, angoli morbidi, niente ombra. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, containerColor: Color? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerLowest)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { Column { content() } }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { Column { content() } }
    }
}

/** Importo che scorre verso il nuovo valore quando cambia. */
@Composable
fun AnimatedMoney(cents: Long, style: TextStyle, color: Color = Color.Unspecified, prefix: String = "", suffix: String = "") {
    val anim = remember { Animatable(cents.toFloat()) }
    LaunchedEffect(cents) { anim.animateTo(cents.toFloat(), tween(600, easing = FastOutSlowInEasing)) }
    Text(
        prefix + Money.format(Math.round(anim.value.toDouble())) + suffix,
        style = style.copy(fontFeatureSettings = TABULAR),
        color = color,
    )
}

/** Anello di avanzamento con contenuto al centro. [fraction] oltre 1 = anello pieno. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    trackColor: Color,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Colore del budget in base a quanto è stato usato. */
@Composable
fun budgetColor(fraction: Float, alertPercent: Int): Color = when {
    fraction > 1f -> AppTheme.colors.danger
    fraction >= alertPercent / 100f -> AppTheme.colors.warning
    else -> AppTheme.colors.good
}

/** Cerchio colorato con l'emoji della categoria. */
@Composable
fun CategoryAvatar(category: Category?, size: Dp = 42.dp) {
    val color = AppTheme.colors.category(category?.id)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.18f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(category?.emoji ?: "❔", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Ciambella: una fetta per valore, con piccoli spazi tra le fette. */
@Composable
fun Donut(values: List<Pair<Color, Long>>, modifier: Modifier = Modifier, stroke: Dp = 18.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val total = values.sumOf { it.second }.toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val arcSize = Size(size.minDimension - s, size.minDimension - s)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            if (total <= 0f) return@Canvas
            val gap = if (values.size > 1) 2.5f else 0f
            var start = -90f
            values.forEach { (color, value) ->
                val sweep = 360f * value / total
                if (sweep > gap) drawArc(color, start + gap / 2, (sweep - gap) * progress.value, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        content()
    }
}
