package com.daybook.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Свои значки — тонкие линии, как в макете (без библиотеки Material). */
object Icons {
    private fun line(vararg paths: String, width: Float = 1.8f): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun solid(vararg paths: String): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (p in paths) b.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
        return b.build()
    }

    val mic = line(
        "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1 -6 0z",
        "M5.5 11a6.5 6.5 0 0 0 13 0",
        "M12 17.5V21",
        width = 2.1f,
    )
    val stop = solid("M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-10a3 3 0 0 1 3 -3z")
    val settings = line(
        "M4 7h10", "M18 7h2", "M4 17h4", "M12 17h8",
        "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val up = line("M6 15l6 -6l6 6")
    val back = line("M14.5 5l-6.5 7l6.5 7")
    val trash = line("M4 7h16", "M9 7V4.5h6V7", "M6.5 7l1 12.5h9l1 -12.5", "M10 11v5", "M14 11v5")
    val backspace = line("M9 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1h-10l-5 -6z", "M12 10l4 4", "M16 10l-4 4")
    val fingerprint = line(
        "M7.5 5.8a8 8 0 0 1 12.5 6.2v1",
        "M4 12a8 8 0 0 1 1.3 -4.4",
        "M8 19.5a12 12 0 0 0 1.5 -6.5a2.5 2.5 0 0 1 5 0a15 15 0 0 1 -1 5",
        "M5 16.5a10 10 0 0 0 .5 -3.5a6.5 6.5 0 0 1 13 0",
        "M12 13a17 17 0 0 1 -2.3 8",
        "M17.3 18a20 20 0 0 0 .7 -4",
    )
    val clock = line("M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0 -17z", "M12 7.5v4.5l3 2")
}

@Composable
fun Icon(icon: ImageVector, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
