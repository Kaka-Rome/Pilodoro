package com.pomodorofocus.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pomodorofocus.app.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 6f
private const val OUTPUT_WIDTH_PX = 1080

/**
 * Editor wallpaper sederhana layar penuh: cubit untuk zoom, geser untuk memindahkan,
 * tombol putar 90°, dan crop otomatis sesuai rasio layar HP.
 * Semua proses terjadi di dalam aplikasi (offline); hasil crop dikirim lewat [onConfirm].
 */
@Composable
fun WallpaperEditorDialog(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    /** Ruang untuk status bar dan tombol navigasi sistem (dihitung dari layar utama). */
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    // Rasio bingkai crop = rasio layar HP (potret), supaya hasilnya pas saat jadi latar timer
    val shortSide = min(metrics.widthPixels, metrics.heightPixels).toFloat()
    val longSide = max(metrics.widthPixels, metrics.heightPixels).toFloat()
    val aspect = shortSide / longSide // lebar / tinggi, selalu <= 1

    // Status editor
    var zoom by remember { mutableFloatStateOf(1f) }          // pengali di atas skala "menutup bingkai"
    var offset by remember { mutableStateOf(Offset.Zero) }    // geseran gambar dari pusat bingkai (px layar)
    var rotationSteps by remember { mutableIntStateOf(0) }    // 0..3 kali 90 derajat

    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("wallpaper_editor"),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                // Judul + petunjuk
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.wallpaper_editor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.wallpaper_editor_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Area kerja
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val areaW = constraints.maxWidth.toFloat()
                    val areaH = constraints.maxHeight.toFloat()

                    // Bingkai crop: sebesar mungkin dengan rasio layar, diberi margin
                    val margin = 16.dp.value * metrics.density
                    // Baris tombol (Rotate, Reset, Cancel, Save) ada di ATAS bingkai, jauh dari
                    // tombol navigasi sistem. Di bawah disisakan ruang aman agar bingkai tidak
                    // tertutup tombol Home/Back.
                    val topReserve = 64.dp.value * metrics.density
                    val bottomReserve = 72.dp.value * metrics.density
                    val availW = (areaW - 2 * margin).coerceAtLeast(1f)
                    val availH = (areaH - 2 * margin - topReserve - bottomReserve).coerceAtLeast(1f)
                    val frameW: Float
                    val frameH: Float
                    if (availW / availH < aspect) {
                        frameW = availW
                        frameH = availW / aspect
                    } else {
                        frameH = availH
                        frameW = availH * aspect
                    }
                    val center = Offset(areaW / 2f, (areaH + topReserve - bottomReserve) / 2f)

                    // Dimensi gambar setelah diputar
                    val rotated = rotationSteps % 2 == 1
                    val imgW = if (rotated) bmpH else bmpW
                    val imgH = if (rotated) bmpW else bmpH
                    val baseScale = max(frameW / imgW, frameH / imgH)

                    // Batasi geseran agar gambar selalu menutup penuh bingkai
                    fun clampOffset(o: Offset, z: Float): Offset {
                        val s = baseScale * z
                        val maxX = max(0f, (imgW * s - frameW) / 2f)
                        val maxY = max(0f, (imgH * s - frameH) / 2f)
                        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                    }

                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(frameW, frameH, rotationSteps) {
                                detectTransformGestures { centroid, pan, zoomChange, _ ->
                                    val oldScale = baseScale * zoom
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                    val newScale = baseScale * newZoom
                                    // Zoom mengarah ke titik di antara dua jari
                                    val fromCenter = centroid - center
                                    val moved = (offset - fromCenter) * (newScale / oldScale) + fromCenter + pan
                                    zoom = newZoom
                                    offset = clampOffset(moved, newZoom)
                                }
                            }
                    ) {
                        val scaleNow = baseScale * zoom
                        val imageCenter = center + offset

                        // Gambar yang sedang diedit
                        translate(left = imageCenter.x, top = imageCenter.y) {
                            rotate(degrees = rotationSteps * 90f, pivot = Offset.Zero) {
                                scale(scale = scaleNow, pivot = Offset.Zero) {
                                    drawImage(
                                        image = imageBitmap,
                                        topLeft = Offset(-bmpW / 2f, -bmpH / 2f)
                                    )
                                }
                            }
                        }

                        // Gelapkan area di luar bingkai crop
                        val left = center.x - frameW / 2f
                        val top = center.y - frameH / 2f
                        val shade = Color.Black.copy(alpha = 0.65f)
                        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, top))
                        drawRect(shade, topLeft = Offset(0f, top + frameH), size = Size(size.width, size.height - top - frameH))
                        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, frameH))
                        drawRect(shade, topLeft = Offset(left + frameW, top), size = Size(size.width - left - frameW, frameH))

                        // Garis bingkai
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(frameW, frameH),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Tombol Save butuh ukuran bingkai & skala saat ini; hitung di sini lewat lambda
                    EditorActions(
                        onRotate = {
                            rotationSteps = (rotationSteps + 1) % 4
                            // Setelah diputar, dimensi berubah: jaga agar tetap menutup bingkai
                            val r = rotationSteps % 2 == 1
                            val iw = if (r) bmpH else bmpW
                            val ih = if (r) bmpW else bmpH
                            val bs = max(frameW / iw, frameH / ih)
                            val s = bs * zoom
                            val mx = max(0f, (iw * s - frameW) / 2f)
                            val my = max(0f, (ih * s - frameH) / 2f)
                            offset = Offset(offset.x.coerceIn(-mx, mx), offset.y.coerceIn(-my, my))
                        },
                        onReset = {
                            zoom = 1f
                            offset = Offset.Zero
                            rotationSteps = 0
                        },
                        onCancel = onCancel,
                        onSave = {
                            val result = renderCrop(
                                source = bitmap,
                                aspect = aspect,
                                frameWidthPx = frameW,
                                scale = baseScale * zoom,
                                offset = offset,
                                rotationSteps = rotationSteps
                            )
                            onConfirm(result)
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorActions(
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onRotate,
                modifier = Modifier.testTag("wallpaper_rotate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = stringResource(R.string.wallpaper_editor_rotate),
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onReset,
                modifier = Modifier.testTag("wallpaper_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.wallpaper_editor_reset),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("wallpaper_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = Color.White)
            }
            Button(
                onClick = onSave,
                modifier = Modifier.testTag("wallpaper_save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_save))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Menggambar ulang isi bingkai crop ke bitmap baru berukuran tetap (lebar 1080 px,
 * tinggi mengikuti rasio layar). Transformasi sama persis dengan yang terlihat di editor.
 */
private fun renderCrop(
    source: Bitmap,
    aspect: Float,
    frameWidthPx: Float,
    scale: Float,
    offset: Offset,
    rotationSteps: Int
): Bitmap {
    val outW = OUTPUT_WIDTH_PX
    val outH = (OUTPUT_WIDTH_PX / aspect).roundToInt()
    val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    val factor = outW / frameWidthPx // dari piksel layar editor ke piksel hasil

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.save()
    canvas.translate(outW / 2f + offset.x * factor, outH / 2f + offset.y * factor)
    canvas.rotate(rotationSteps * 90f)
    canvas.scale(scale * factor, scale * factor)
    canvas.drawBitmap(source, -source.width / 2f, -source.height / 2f, paint)
    canvas.restore()
    return result
}
