package com.vinalayan.bancada

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import com.vinalayan.bancada.protocol.PanelLink
import com.vinalayan.bancada.protocol.PanelSnapshot
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Approximate round 4-inch Tripper Dash of the Himalayan 450.
 * Layout follows the owner's-manual family (analog default, digital map,
 * trip/range/consumption/battery/service/temperature pages) and is marked
 * "aproximado" in CHECKLIST_TELAS.md — it is not a photograph of the BR bike.
 */
@Composable
fun ClusterCanvas(
    bike: BikeSim,
    page: ClusterPage,
    snap: PanelSnapshot,
    modifier: Modifier = Modifier,
) {
    val night = !bike.day
    val bg = if (night) Color(0xFF14161A) else Color(0xFFE7E1D4)
    val ink = if (night) Color(0xFFF3F0E8) else Color(0xFF1B1A17)
    val dim = if (night) Color(0xFF9AA0A6) else Color(0xFF5E5A53)
    val arc = if (night) Color(0xFF3A3E46) else Color(0xFFC9C2B4)
    val hot = Color(0xFFE23B2F)
    val eco = Color(0xFF2E9E62)
    Canvas(modifier.fillMaxSize()) {
        val d = min(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = d / 2f
        drawCircle(if (night) Color(0xFF0A0B0D) else Color(0xFFB7B1A6), r, c)
        drawCircle(bg, r * 0.94f, c)
        drawCircle(arc, r * 0.94f, c, style = Stroke(r * 0.012f))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink.toArgb()
            textAlign = Paint.Align.CENTER
        }
        val dimPaint = Paint(paint).apply { color = dim.toArgb() }

        fun text(s: String, x: Float, y: Float, sizePx: Float, p: Paint = paint) {
            p.textSize = sizePx
            drawContext.canvas.nativeCanvas.drawText(s, x, y, p)
        }

        val clock = snap.clock ?: "%02d:%02d".format(
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            Calendar.getInstance().get(Calendar.MINUTE),
        )
        text(clock, c.x - r * 0.38f, c.y - r * 0.62f, r * 0.09f, dimPaint)
        text("${bike.outsideC}°C", c.x + r * 0.38f, c.y - r * 0.62f, r * 0.08f, dimPaint)
        val modeColor = if (bike.mode.eco) eco else ink
        text(bike.mode.label, c.x, c.y - r * 0.46f, r * 0.09f, Paint(paint).apply { color = modeColor.toArgb() })
        if (bike.mode.rearAbsOff) text("ABS TR.", c.x, c.y - r * 0.34f, r * 0.055f, dimPaint)

        val lamps = listOf(
            bike.leftTurn to "◀",
            bike.highBeam to "BEAM",
            bike.absWarn to "ABS",
            bike.engineWarn to "ENG",
            bike.sideStand to "ST",
            bike.rightTurn to "▶",
        )
        lamps.forEachIndexed { i, (on, label) ->
            val x = c.x - r * 0.42f + i * r * 0.17f
            text(label, x, c.y - r * 0.74f, r * 0.055f, if (on) paint else Paint(dimPaint).apply { alpha = 70 })
        }

        when (page) {
            ClusterPage.ANALOG, ClusterPage.DIGITAL -> {
                val sweep = 250f
                val start = 145f
                drawArc(
                    arc,
                    start,
                    sweep,
                    false,
                    topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f),
                    size = Size(r * 1.56f, r * 1.56f),
                    style = Stroke(r * 0.03f, cap = StrokeCap.Round),
                )
                val rpmFrac = (bike.rpm / 9000f).coerceIn(0f, 1f)
                drawArc(
                    if (bike.rpm > 7500) hot else ink,
                    start,
                    sweep * rpmFrac,
                    false,
                    topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f),
                    size = Size(r * 1.56f, r * 1.56f),
                    style = Stroke(r * 0.03f, cap = StrokeCap.Round),
                )
                val redStart = start + sweep * (7500f / 9000f)
                drawArc(
                    hot.copy(alpha = 0.85f),
                    redStart,
                    start + sweep - redStart,
                    false,
                    topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f),
                    size = Size(r * 1.56f, r * 1.56f),
                    style = Stroke(r * 0.012f, cap = StrokeCap.Butt),
                )
                if (page == ClusterPage.ANALOG) {
                    text(bike.speed.toString(), c.x, c.y + r * 0.08f, r * 0.34f)
                    text("km/h", c.x, c.y + r * 0.2f, r * 0.07f, dimPaint)
                } else {
                    text(bike.speed.toString(), c.x - r * 0.48f, c.y + r * 0.02f, r * 0.16f)
                    text("km/h", c.x - r * 0.48f, c.y + r * 0.12f, r * 0.05f, dimPaint)
                    val nav = if (snap.videoFrames > 0) "MAPA" else "SEM VÍDEO"
                    text(nav, c.x + r * 0.12f, c.y + r * 0.42f, r * 0.055f, dimPaint)
                }
                val gear = if (bike.gear == 0) "N" else bike.gear.toString()
                text(gear, c.x, c.y + r * 0.38f, r * 0.14f)
            }
            ClusterPage.TRIP_A -> bigInfo("TRIP A", "%.1f km".format(bike.tripA), c, r, paint, dimPaint)
            ClusterPage.TRIP_B -> bigInfo("TRIP B", "%.1f km".format(bike.tripB), c, r, paint, dimPaint)
            ClusterPage.RANGE -> bigInfo("AUTONOMIA", "${fuelRange(bike)} km", c, r, paint, dimPaint)
            ClusterPage.CONSUME -> bigInfo("CONSUMO", "%.1f km/l".format(bike.consumption), c, r, paint, dimPaint)
            ClusterPage.BATTERY -> bigInfo("BATERIA", "%.1f V".format(bike.volts), c, r, paint, dimPaint)
            ClusterPage.SERVICE -> bigInfo("REVISÃO", "${bike.serviceKm} km", c, r, paint, dimPaint)
            ClusterPage.TEMP -> bigInfo("MOTOR", "${bike.tempC}°C", c, r, paint, dimPaint)
        }

        val bars = 8
        val bw = r * 0.045f
        val gap = r * 0.012f
        val total = bars * bw + (bars - 1) * gap
        val x0 = c.x - total / 2f
        for (i in 0 until bars) {
            val on = i < bike.fuelBars
            drawRoundRect(
                color = when {
                    !on -> arc
                    bike.fuelBars <= 2 -> hot
                    else -> ink
                },
                topLeft = Offset(x0 + i * (bw + gap), c.y + r * 0.52f),
                size = Size(bw, r * 0.07f),
            )
        }
        text("ODO ${bike.odoKm} km", c.x, c.y + r * 0.72f, r * 0.07f, dimPaint)
        val media = snap.nowPlaying
        if (media != null && media.title.isNotBlank()) {
            text(media.title.take(18), c.x, c.y + r * 0.82f, r * 0.055f)
            text(media.artist.take(18), c.x, c.y + r * 0.89f, r * 0.045f, dimPaint)
        }
        val caller = snap.caller
        if (!caller.isNullOrBlank()) {
            text("CHAMADA", c.x, c.y - r * 0.08f, r * 0.06f, Paint(paint).apply { color = hot.toArgb() })
            text(caller.take(16), c.x, c.y + r * 0.02f, r * 0.08f)
        }
        val linkLabel = when (snap.link) {
            PanelLink.WAITING -> "AGUARDANDO"
            PanelLink.AUTHENTICATING -> "AUTENTICANDO"
            PanelLink.AUTHENTICATED -> "AUTENTICADO"
            PanelLink.PROJECTING -> "PROJETANDO"
            PanelLink.IGNITION_OFF -> "IGNIÇÃO OFF"
            PanelLink.LINK_DOWN -> "SEM LINK"
        }
        text(linkLabel, c.x, c.y - r * 0.86f, r * 0.05f, dimPaint)
        // Needle tip for the tach, analog only.
        if (page == ClusterPage.ANALOG || page == ClusterPage.DIGITAL) {
            val ang = Math.toRadians((145.0 + 250.0 * (bike.rpm / 9000f).coerceIn(0f, 1f)))
            val tip = Offset(
                c.x + (r * 0.7f * cos(ang)).toFloat(),
                c.y + (r * 0.7f * sin(ang)).toFloat(),
            )
            drawLine(ink, c, tip, strokeWidth = r * 0.012f, cap = StrokeCap.Round)
            drawCircle(ink, r * 0.025f, c)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.bigInfo(
    title: String,
    value: String,
    c: Offset,
    r: Float,
    paint: Paint,
    dim: Paint,
) {
    dim.textSize = r * 0.08f
    dim.textAlign = Paint.Align.CENTER
    drawContext.canvas.nativeCanvas.drawText(title, c.x, c.y - r * 0.08f, dim)
    paint.textSize = r * 0.2f
    paint.textAlign = Paint.Align.CENTER
    drawContext.canvas.nativeCanvas.drawText(value, c.x, c.y + r * 0.16f, paint)
}

fun fuelRange(bike: BikeSim): Int {
    val litres = 17.0 * bike.fuelBars / 8.0
    val kmPerL = if (bike.consumption > 0) bike.consumption else 20.0
    return (litres * kmPerL).toInt()
}
