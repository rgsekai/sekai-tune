package moe.rgsekai.sekaitune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints

// bold is a little wider than semibold, so a long resting line breaks where its bold self will and doesn't gain or lose a row when it lights up
@Composable
internal fun rememberSteadyWrap(text: String, boldStyle: TextStyle): SteadyWrap {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    return remember(text, boldStyle, measurer) { SteadyWrap(text, boldStyle, measurer) }
}

// put the modifier on whatever holds the line at its full width, and show text instead of the raw line
internal class SteadyWrap(
    private val source: String,
    private val boldStyle: TextStyle,
    private val measurer: TextMeasurer,
) {
    private val shown = mutableStateOf(source)

    val text: String get() = shown.value

    val modifier: Modifier = Modifier.layout { measurable, constraints ->
        if (constraints.hasBoundedWidth && source.isNotEmpty()) {
            val bold = measurer.measure(
                text = source,
                style = boldStyle,
                constraints = Constraints(maxWidth = constraints.maxWidth),
                layoutDirection = layoutDirection,
                density = this,
            )
            val steady = breakLike(source, bold)
            if (steady != shown.value) shown.value = steady
        }
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

// the same characters with each space the bold line wraps at turned into a line break, so every offset stays where it was
private fun breakLike(text: String, bold: TextLayoutResult): String {
    if (bold.lineCount <= 1) return text
    var chars: CharArray? = null
    for (line in 0 until bold.lineCount - 1) {
        val at = bold.getLineEnd(line) - 1
        if (at !in text.indices || text[at] != ' ') continue
        val out = chars ?: text.toCharArray().also { chars = it }
        out[at] = '\n'
    }
    return chars?.let { String(it) } ?: text
}

