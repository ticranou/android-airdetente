package com.airchecklists.app.ui.efis.gauges.nav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.terrain.QfuParser
import java.io.File

private const val PAGE_W = 595   // A4 @72dpi
private const val PAGE_H = 842
private const val MARGIN = 32f

/**
 * Generates a multi-page A4 PDF of the nav plan into the cache and opens it:
 *  - page 1: full-width LOG table + NOTES box + the (real MapLibre) route map;
 *  - one extra page per terrain: page 1 of its downloaded VAC (runways), if present.
 * [mapBitmap] is the MapLibre snapshot (null → schematic fallback).
 */
internal fun exportPdf(
    context: Context,
    route: List<VacChart>,
    steps: List<NavStepExport>,
    notes: String,
    mapBitmap: Bitmap?,
) {
    runCatching {
        val doc = PdfDocument()
        drawMainPage(doc, route, steps, notes, mapBitmap)
        appendVacPages(context, doc, route)

        val out = File(context.cacheDir, "nav_plan.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        val opened = com.airchecklists.app.data.net.PdfOpener.open(context, out, out.absolutePath)
        if (!opened) {
            android.widget.Toast.makeText(context, "Aucune application pour ouvrir le PDF.", android.widget.Toast.LENGTH_LONG).show()
        }
    }.onFailure { e ->
        android.widget.Toast.makeText(context, "Échec de l'export PDF : ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun drawMainPage(
    doc: PdfDocument,
    route: List<VacChart>,
    steps: List<NavStepExport>,
    notes: String,
    mapBitmap: Bitmap?,
) {
    val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
    val c = page.canvas
    val right = PAGE_W - MARGIN
    val title = Paint().apply { color = AColor.BLACK; textSize = 20f; isFakeBoldText = true }
    val hd = Paint().apply { color = AColor.DKGRAY; textSize = 10f; isFakeBoldText = true }
    val p = Paint().apply { color = AColor.BLACK; textSize = 11f }
    val small = Paint().apply { color = AColor.DKGRAY; textSize = 9f }
    val line = Paint().apply { color = AColor.LTGRAY; strokeWidth = 1f; style = Paint.Style.STROKE }
    val rowLine = Paint().apply { color = AColor.rgb(200, 200, 200); strokeWidth = 1f }

    var y = 44f
    c.drawText("Prépa Navigation — AirDetente", MARGIN, y, title); y += 28f

    // ---- Full-width LOG table (narrow value columns, Détails+Notes merged) ----
    c.drawText("LOG", MARGIN, y, hd); y += 6f
    c.drawLine(MARGIN, y, right, y, Paint().apply { color = AColor.LTGRAY; strokeWidth = 1f }); y += 16f
    val xTerrain = MARGIN
    val xRm = 110f; val xDist = 160f; val xTsv = 210f; val xInfo = 262f  // Info = Détails + Notes
    c.drawText("Terrain", xTerrain, y, hd)
    c.drawText("RM", xRm, y, hd)
    c.drawText("DIST", xDist, y, hd)
    c.drawText("TSV", xTsv, y, hd)
    c.drawText("Détails / Notes", xInfo, y, hd)
    y += 6f; c.drawLine(MARGIN, y, right, y, Paint().apply { color = AColor.LTGRAY; strokeWidth = 1f })
    val rowH = 52f  // taller rows → room for handwritten notes
    steps.forEach { s ->
        val baseY = y + 18f
        c.drawText(s.icao, xTerrain, baseY, Paint(p).apply { isFakeBoldText = true })
        c.drawText(s.rm ?: "—", xRm, baseY, p)
        c.drawText(s.dist ?: "—", xDist, baseY, p)
        c.drawText(s.tsv ?: "—", xTsv, baseY, p)
        c.drawText(s.detail, xInfo, baseY, small)
        // Runway circle + designator on the far right of the row.
        s.qfu?.let { drawRunwayCircle(c, right - 26f, y + rowH / 2f, 14f, it, s.designator) }
        y += rowH
        c.drawLine(MARGIN, y, right, y, rowLine) // separator between legs
    }

    // ---- NOTES box (empty if no text) ----
    y += 18f
    c.drawText("NOTES", MARGIN, y, hd); y += 12f
    val notesTop = y; val notesH = 80f
    c.drawRect(MARGIN, notesTop, right, notesTop + notesH, line)
    var ny = notesTop + 16f
    notes.split("\n").forEach { ln -> if (ln.isNotBlank()) { c.drawText(ln, MARGIN + 6f, ny, p); ny += 15f } }
    y = notesTop + notesH + 22f

    // ---- Route map at the bottom (real MapLibre snapshot, else beige) + overlay ----
    c.drawText("CARTE", MARGIN, y, hd); y += 8f
    val mapH = PAGE_H - y - 36f
    val mapW = right - MARGIN
    if (mapBitmap != null) {
        // Aspect-fit the snapshot into the frame (no distortion), centred.
        val s = minOf(mapW / mapBitmap.width, mapH / mapBitmap.height)
        val dw = mapBitmap.width * s; val dh = mapBitmap.height * s
        val dx = MARGIN + (mapW - dw) / 2f; val dy = y + (mapH - dh) / 2f
        c.drawBitmap(mapBitmap, null, RectF(dx, dy, dx + dw, dy + dh), Paint().apply { isFilterBitmap = true })
    } else {
        c.drawRect(MARGIN, y, right, y + mapH, Paint().apply { color = AColor.rgb(233, 230, 222) })
    }
    // Route overlay (branches + dots + ICAO labels), aspect-correct in both cases.
    drawRouteOverlay(c, route, MARGIN, y, mapW, mapH)

    doc.finishPage(page)
}

/** Append page 1 of each terrain's downloaded VAC (runways) as extra pages. */
private fun appendVacPages(context: Context, doc: PdfDocument, route: List<VacChart>) {
    var pageNo = 2
    // De-dupe by ICAO so a return leg doesn't repeat the same VAC.
    route.distinctBy { it.icao.uppercase() }.forEach { chart ->
        val pdf = ServiceLocator.vacRepository.localPdf(chart) ?: return@forEach
        if (!pdf.exists()) return@forEach
        runCatching {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount < 1) return@use
                    renderer.openPage(0).use { vp ->
                        // Render the VAC page 1 to a bitmap, scaled to A4 width.
                        val scale = (PAGE_W - 2 * MARGIN) / vp.width
                        val bw = ((PAGE_W - 2 * MARGIN)).toInt()
                        val bh = (vp.height * scale).toInt().coerceAtMost((PAGE_H - 2 * MARGIN).toInt())
                        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AColor.WHITE)
                        vp.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
                        val c = page.canvas
                        val hd = Paint().apply { color = AColor.DKGRAY; textSize = 11f; isFakeBoldText = true }
                        c.drawText("VAC — ${chart.icao} ${chart.airfieldName}", MARGIN, 30f, hd)
                        c.drawBitmap(bmp, MARGIN, 44f, Paint().apply { isFilterBitmap = true })
                        doc.finishPage(page)
                        bmp.recycle()
                    }
                }
            }
        }
    }
}

/** Row data prepared by the instrument for the PDF (Détails + Notes merged column). */
internal data class NavStepExport(
    val icao: String,
    val rm: String?,
    val dist: String?,
    val tsv: String?,
    val detail: String,
    val qfu: Float? = null,      // runway orientation for the circle glyph
    val designator: String? = null,  // e.g. "04/22"
)

/**
 * Draw the route overlay (branches + dots + ICAO labels) inside the frame,
 * preserving aspect ratio (longitude scaled by cos(lat)) so the shape isn't
 * distorted. Works both over a map snapshot and over a plain background.
 */
private fun drawRouteOverlay(c: android.graphics.Canvas, route: List<VacChart>, left: Float, top: Float, w: Float, h: Float) {
    val pts = route.filter { it.latitude != null && it.longitude != null }
    if (pts.isEmpty()) return
    val lats = pts.map { it.latitude!! }; val lons = pts.map { it.longitude!! }
    val minLat = lats.min(); val maxLat = lats.max(); val minLon = lons.min(); val maxLon = lons.max()
    val midLat = (minLat + maxLat) / 2.0
    val cosLat = kotlin.math.cos(Math.toRadians(midLat)).coerceAtLeast(0.1)
    // World units: longitude compressed by cos(lat) so 1° lon ≈ 1° lat on screen.
    val spanX = ((maxLon - minLon) * cosLat).coerceAtLeast(0.001)
    val spanY = (maxLat - minLat).coerceAtLeast(0.001)
    val pad = 34f
    val iw = w - 2 * pad; val ih = h - 2 * pad
    val scale = minOf(iw / spanX, ih / spanY)   // uniform → no distortion
    val drawnW = (spanX * scale).toFloat(); val drawnH = (spanY * scale).toFloat()
    val ox = left + (w - drawnW) / 2f; val oy = top + (h - drawnH) / 2f
    fun sx(lon: Double) = ox + (((lon - minLon) * cosLat) * scale).toFloat()
    fun sy(lat: Double) = oy + (((maxLat - lat)) * scale).toFloat()

    val green = Paint().apply { color = AColor.rgb(46, 204, 64); strokeWidth = 5f; isAntiAlias = true }
    for (i in 1 until pts.size) {
        c.drawLine(sx(pts[i - 1].longitude!!), sy(pts[i - 1].latitude!!), sx(pts[i].longitude!!), sy(pts[i].latitude!!), green)
    }
    val dot = Paint().apply { color = AColor.rgb(21, 101, 192); isAntiAlias = true }
    val label = Paint().apply { color = AColor.rgb(16, 48, 90); textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
    val halo = Paint().apply { color = AColor.WHITE; textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
    pts.forEach {
        val x = sx(it.longitude!!); val yy = sy(it.latitude!!)
        c.drawCircle(x, yy, 5f, dot)
        // Label placed left of the dot if near the right edge.
        val approxW = it.icao.length * 8f + 8f
        val lx = if (x + 8f + approxW > left + w) x - 8f - approxW else x + 8f
        listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f).forEach { (dxo, dyo) -> c.drawText(it.icao, lx + dxo, yy + 4f + dyo, halo) }
        c.drawText(it.icao, lx, yy + 4f, label)
    }
}

/** Draw a runway-orientation circle (white border, blue strip rotated to [qfu])
 *  centred at (cx,cy) with radius [r], + optional designator below. */
private fun drawRunwayCircle(c: android.graphics.Canvas, cx: Float, cy: Float, r: Float, qfu: Float, designator: String?) {
    val disc = Paint().apply { color = AColor.rgb(26, 32, 38); isAntiAlias = true }
    val border = Paint().apply { color = AColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    c.drawCircle(cx, cy, r, disc)
    c.drawCircle(cx, cy, r, border)
    c.save()
    c.rotate(qfu, cx, cy)
    val strip = Paint().apply { color = AColor.rgb(58, 110, 165); strokeWidth = 7f; isAntiAlias = true }
    val len = r * 0.72f
    c.drawLine(cx, cy - len, cx, cy + len, strip)
    c.restore()
    designator?.let {
        val t = Paint().apply { color = AColor.rgb(232, 132, 58); textSize = 9f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        c.drawText(it, cx, cy + r + 11f, t)
    }
}

/** Detail line for a terrain (name · freq · QFU · alt). */
internal fun terrainDetail(chart: VacChart): String {
    val parts = mutableListOf<String>()
    chart.frequencies.takeIf { it.isNotBlank() }?.let { parts += it }
    QfuParser.primaryHeading(chart.circuit)?.let { parts += "QFU %03d°".format(it) }
    chart.altitude.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}
