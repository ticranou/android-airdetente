package com.airchecklists.app.ui.efis.gauges.vac

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideBorder
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import com.airchecklists.app.ui.efis.gauges.CellBackground
import com.airchecklists.app.ui.efis.gauges.LocalCellBackground
import com.airchecklists.app.ui.efis.gauges.drawCellBackground
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NUMVAC — Carte VAC intégrée (330 dp).
 *
 * Affiche la carte VAC PDF du terrain cible partagé (lockedApproachTarget / AUTO).
 * Pinch-to-zoom, pan, double-tap pour réinitialiser. Navigation entre pages via boutons
 * numérotés en bas. Bouton Lock/Unlock et Négatif (coin bas-droit).
 *
 * Le zoom, le pan, le lock et la page courante sont conservés pendant toute la session.
 * Seul un changement de cible d'approche (lockedTarget) réinitialise ces paramètres.
 */
@Composable
fun VacInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideTitle = LocalHideTitle.current
    val hideCellBorder = LocalHideBorder.current

    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    val lockedTarget by ServiceLocator.lockedApproachTarget.collectAsStateWithLifecycle()
    val efisState by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()

    // Résoudre le VacChart correspondant à la cible.
    val chart = remember(lockedTarget, charts, efisState.hasPosition,
        efisState.latitude, efisState.longitude) {
        val icao = lockedTarget?.icao
        if (icao != null) {
            charts.firstOrNull { it.icao.equals(icao, ignoreCase = true) }
        } else {
            val lat = efisState.latitude
            val lon = efisState.longitude
            if (efisState.hasPosition) {
                charts
                    .filter { it.isDownloaded && it.latitude != null && it.longitude != null }
                    .minByOrNull { c ->
                        val dlat = c.latitude!! - lat
                        val dlon = c.longitude!! - lon
                        dlat * dlat + dlon * dlon
                    }
            } else null
        }
    }

    val pdfFile: File? = chart?.let { ServiceLocator.vacRepository.localPdf(it) }

    // ── États persistants session (survivent aux changements de cockpit) ─────────
    // Clé stable sur l'icao de la cible (ou "auto") pour isoler les VACs entre elles.
    val targetKey = lockedTarget?.icao ?: "auto"

    @Suppress("UNCHECKED_CAST")
    val scaleState = remember {
        ServiceLocator.instrumentState("vac.scale") { mutableFloatStateOf(1f) }
    } as androidx.compose.runtime.MutableFloatState

    @Suppress("UNCHECKED_CAST")
    val offsetState = remember {
        ServiceLocator.instrumentState("vac.offset") { mutableStateOf(Offset.Zero) }
    } as androidx.compose.runtime.MutableState<Offset>

    @Suppress("UNCHECKED_CAST")
    val lockedState = remember {
        ServiceLocator.instrumentState("vac.locked") { mutableStateOf(false) }
    } as androidx.compose.runtime.MutableState<Boolean>

    @Suppress("UNCHECKED_CAST")
    val negativeState = remember {
        ServiceLocator.instrumentState("vac.negative") { mutableStateOf(false) }
    } as androidx.compose.runtime.MutableState<Boolean>

    @Suppress("UNCHECKED_CAST")
    val pageState = remember {
        ServiceLocator.instrumentState("vac.page") { mutableIntStateOf(0) }
    } as androidx.compose.runtime.MutableIntState

    @Suppress("UNCHECKED_CAST")
    val lastTargetState = remember {
        ServiceLocator.instrumentState("vac.lastTarget") { mutableStateOf<String?>(null) }
    } as androidx.compose.runtime.MutableState<String?>

    // Réinitialiser zoom/pan/lock/page quand la cible change.
    LaunchedEffect(targetKey) {
        if (lastTargetState.value != targetKey) {
            lastTargetState.value = targetKey
            scaleState.floatValue = 1f
            offsetState.value = Offset.Zero
            lockedState.value = false
            pageState.intValue = 0
        }
    }

    var scale by scaleState
    var offset by offsetState
    var locked by lockedState
    var negative by negativeState
    var currentPage by pageState

    // Nombre de pages du PDF.
    var pageCount by remember(pdfFile) { mutableIntStateOf(0) }
    DisposableEffect(pdfFile) {
        if (pdfFile != null) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                pageCount = r.pageCount
                r.close()
                pfd.close()
            }
        } else {
            pageCount = 0
            currentPage = 0
        }
        onDispose { }
    }
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && currentPage >= pageCount) currentPage = pageCount - 1
    }

    // Rendu bitmap en background.
    var bitmap by remember(pdfFile, currentPage) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(pdfFile, currentPage) { mutableStateOf(false) }
    LaunchedEffect(pdfFile, currentPage) {
        bitmap = null
        renderError = false
        if (pdfFile == null || !pdfFile.exists()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(currentPage.coerceIn(0, renderer.pageCount - 1))
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bmp
            }.onSuccess { bitmap = it }.onFailure { renderError = true }
        }
    }

    val cellBg = LocalCellBackground.current

    val invertFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f,
        )))
    }

    Box(modifier = modifier.fillMaxSize()
        .pointerInput(pdfFile, locked) {
            awaitPointerEventScope {
                var prevDist = -1f
                var prevMid = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    when {
                        pressed.size >= 2 -> {
                            event.changes.forEach { it.consume() }
                            if (!locked) {
                                val p1 = pressed[0].position
                                val p2 = pressed[1].position
                                val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                val dist = (p2 - p1).getDistance().coerceAtLeast(1f)
                                if (prevDist > 0f) {
                                    val zoomDelta = dist / prevDist
                                    scale = (scale * zoomDelta).coerceIn(0.3f, 10f)
                                    offset += (mid - prevMid)
                                }
                                prevDist = dist
                                prevMid = mid
                            }
                        }
                        else -> {
                            prevDist = -1f
                            prevMid = Offset.Zero
                        }
                    }
                }
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentPage, pageCount) {
                    detectTapGestures(
                        onDoubleTap = { if (!locked) { scale = 1f; offset = Offset.Zero } },
                        onTap = { tap ->
                            if (pageCount > 1 && !locked) {
                                val footerH = 32.dp.roundToPx()
                                val footerTop = size.height - footerH
                                if (tap.y >= footerTop) {
                                    val btnW = size.width / pageCount
                                    val tappedPage = (tap.x / btnW).toInt().coerceIn(0, pageCount - 1)
                                    currentPage = tappedPage
                                }
                            }
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            drawCellBackground(cellBg)

            val headerH = if (hideTitle) 0f else 22.dp.toPx().coerceAtMost(h * 0.12f)
            val footerH = if (pageCount > 1) 32.dp.toPx() else 0f
            val bodyTop = headerH
            val bodyH = h - headerH - footerH

            val bmp = bitmap
            when {
                bmp != null -> {
                    withTransform({
                        clipRect(0f, bodyTop, w, bodyTop + bodyH)
                        translate(left = w / 2f + offset.x, top = bodyTop + bodyH / 2f + offset.y)
                        scale(scale, scale)
                    }) {
                        val bmpW = bmp.width.toFloat()
                        val bmpH = bmp.height.toFloat()
                        val fitScale = minOf(w / bmpW, bodyH / bmpH)
                        withTransform({ scale(fitScale, fitScale) }) {
                            drawImage(
                                bmp.asImageBitmap(),
                                topLeft = Offset(-bmpW / 2f, -bmpH / 2f),
                                colorFilter = if (negative) invertFilter else null,
                            )
                        }
                    }
                }
                renderError ->
                    compactText(tm, "Erreur lecture VAC", w / 2f, bodyTop + bodyH / 2f,
                        sizeSp = 13f, color = CompactStyle.Descent)
                pdfFile == null && chart != null && !chart.isDownloaded ->
                    compactText(tm, "VAC non disponible — ${chart.icao}", w / 2f, bodyTop + bodyH / 2f,
                        sizeSp = 12f, color = CompactStyle.Dim)
                chart == null ->
                    compactText(tm, "Aucun terrain — appui long", w / 2f, bodyTop + bodyH / 2f,
                        sizeSp = 12f, color = CompactStyle.Dim)
            }

            // Bande de navigation entre pages — boutons numérotés.
            if (pageCount > 1) {
                val fy = h - footerH
                drawRect(Color(0xFF1E1E1E), topLeft = Offset(0f, fy), size = Size(w, footerH))
                drawLine(Color(0xFF3A3A3A), Offset(0f, fy), Offset(w, fy), strokeWidth = 1f)

                val btnW = w / pageCount
                for (p in 0 until pageCount) {
                    val bx = p * btnW
                    val isActive = p == currentPage
                    if (isActive) {
                        drawRect(
                            Color(0xFF2A4A6A),
                            topLeft = Offset(bx + 1f, fy + 1f),
                            size = Size(btnW - 2f, footerH - 2f),
                        )
                    }
                    compactText(
                        tm, "${p + 1}",
                        bx + btnW / 2f, fy + footerH / 2f,
                        sizeSp = 13f,
                        color = if (isActive) CompactStyle.Accent else CompactStyle.Dim,
                        bold = isActive,
                    )
                    if (p > 0) drawLine(
                        Color(0xFF3A3A3A),
                        Offset(bx, fy + footerH * 0.2f),
                        Offset(bx, fy + footerH * 0.8f),
                        strokeWidth = 1f,
                    )
                }
            }

            if (!hideCellBorder) drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))

            if (headerH > 0f) {
                drawNumTitleBar(bezel, w, headerH)
                val title = chart?.icao?.let { "VAC · $it" } ?: "VAC"
                compactText(tm, title, w / 2f, headerH / 2f, sizeSp = 13f, color = CompactStyle.Dim)
            }
        }

        // Spinner pendant le rendu.
        if (bitmap == null && pdfFile != null && !renderError) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                color = CompactStyle.Accent,
                strokeWidth = 2.dp,
            )
        }

        // Boutons Lock/Unlock et Négatif — coin bas-droit, empilés au-dessus du footer.
        // On utilise Box+clickable plutôt qu'IconButton pour éviter la zone de touch
        // minimale de 48dp imposée par Material qui crée des chevauchements.
        val btnSize = 28.dp
        val btnPad = 4.dp

        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier.padding(end = btnPad, bottom = btnPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Bouton Négatif
                Box(
                    modifier = Modifier
                        .padding(end = btnPad)
                        .size(btnSize)
                        .background(Color(0xAA000000), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { negative = !negative },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.InvertColors,
                        contentDescription = if (negative) "Mode normal" else "Mode négatif",
                        tint = if (negative) Color(0xFF4FC3F7) else Color(0xCCFFFFFF),
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Bouton Lock/Unlock
                Box(
                    modifier = Modifier
                        .size(btnSize)
                        .background(Color(0xAA000000), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { locked = !locked },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (locked) "Déverrouiller" else "Verrouiller",
                        tint = if (locked) Color(0xFFFFCC44) else Color(0xCCFFFFFF),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // Spacer transparent de la hauteur exacte du footer pour que la Row se
            // positionne juste au-dessus sans chevauchement.
            if (pageCount > 1) Box(modifier = Modifier.size(width = 1.dp, height = 32.dp))
        }
    }
}
