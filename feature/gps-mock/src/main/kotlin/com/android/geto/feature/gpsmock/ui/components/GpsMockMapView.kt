/*
 *
 *   Copyright 2023 Einstein Blanco
 *
 *   Licensed under the GNU General Public License v3.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.gnu.org/licenses/gpl-3.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.android.geto.feature.gpsmock.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.geto.feature.gpsmock.model.MockCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * 100% Native Jetpack Compose OpenStreetMap Slippy Tile Engine.
 *
 * Direct, hardware-accelerated rendering of real OpenStreetMap raster tiles
 * directly on Compose Canvas. Zero WebView overhead, zero blank-screen bugs,
 * instant smooth pinch-to-zoom, free uncentered panning, interactive pin placement,
 * and high-speed disk & memory tile caching.
 */
@Composable
fun GpsMockMapView(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double,
    bearing: Float,
    accuracy: Float,
    isMocking: Boolean,
    mapLayer: String,
    routeWaypoints: List<MockCoordinate>,
    forceCenterTrigger: Long = 0L,
    onLocationSelected: (lat: Double, lng: Double) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tileManager = remember { OsmTileManager.getInstance(context) }

    // Map viewport state (Center of map view)
    var viewLat by remember { mutableDoubleStateOf(latitude) }
    var viewLng by remember { mutableDoubleStateOf(longitude) }
    var zoomLevel by remember { mutableFloatStateOf(15.0f) }

    // Recomposition trigger when new tiles finish downloading
    var tileEpoch by remember { mutableIntStateOf(0) }

    // When mocking is active, follow the mock pin
    LaunchedEffect(latitude, longitude, isMocking) {
        if (isMocking) {
            viewLat = latitude
            viewLng = longitude
        }
    }

    // Force map to center on the pin when user triggers recenter or starts mocking
    LaunchedEffect(forceCenterTrigger) {
        if (forceCenterTrigger > 0L) {
            viewLat = latitude
            viewLng = longitude
        }
    }

    // Pulse animation for active mocking pin
    val infiniteTransition = rememberInfiniteTransition(label = "mapPulse")
    val pulseRingRadius by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseRadius",
    )
    val pulseRingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    // Gesture handling: Pinch to zoom & pan
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomLevel = (zoomLevel * zoomChange).coerceIn(2.0f, 19.0f)

        // Convert screen pan to coordinate delta
        val metersPerPx = calculateMetersPerPixel(viewLat, zoomLevel)
        val latDelta = (panChange.y * metersPerPx) / 111320.0
        val lngDelta = -(panChange.x * metersPerPx) / (111320.0 * cos(Math.toRadians(viewLat)).coerceAtLeast(0.01))

        viewLat = (viewLat + latDelta).coerceIn(-85.0, 85.0)
        viewLng = ((viewLng + lngDelta + 180.0) % 360.0) - 180.0
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE5E7EB))
                .transformable(state = transformableState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            zoomLevel = (zoomLevel + 1.2f).coerceAtMost(19.0f)
                        },
                        onTap = { tapOffset ->
                            val screenWidth = size.width.toFloat()
                            val screenHeight = size.height.toFloat()

                            // Calculate tapped coordinate from screen offset
                            val tappedCoord = screenToCoordinate(
                                screenOffset = tapOffset,
                                centerLat = viewLat,
                                centerLng = viewLng,
                                zoom = zoomLevel,
                                screenWidth = screenWidth,
                                screenHeight = screenHeight,
                            )
                            onLocationSelected(tappedCoord.latitude, tappedCoord.longitude)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val metersPerPx = calculateMetersPerPixel(viewLat, zoomLevel)
                        val latDelta = (dragAmount.y * metersPerPx) / 111320.0
                        val lngDelta = -(dragAmount.x * metersPerPx) / (111320.0 * cos(Math.toRadians(viewLat)).coerceAtLeast(0.01))

                        viewLat = (viewLat + latDelta).coerceIn(-85.0, 85.0)
                        viewLng = ((viewLng + lngDelta + 180.0) % 360.0) - 180.0
                    }
                },
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            // Read tileEpoch to trigger recomposition when tiles arrive
            @Suppress("UNUSED_VARIABLE")
            val epoch = tileEpoch

            // 1. Draw OpenStreetMap Slippy Tiles
            val intZoom = zoomLevel.toInt().coerceIn(1, 19)
            val subZoomScale = 2.0.pow((zoomLevel - intZoom).toDouble()).toFloat()
            val tileSize = (256f * subZoomScale)

            val centerWorld = coordinateToWorld(viewLat, viewLng, intZoom)
            val minXWorld = centerWorld.x - (centerX / subZoomScale)
            val maxXWorld = centerWorld.x + (centerX / subZoomScale)
            val minYWorld = centerWorld.y - (centerY / subZoomScale)
            val maxYWorld = centerWorld.y + (centerY / subZoomScale)

            val minTileX = floor(minXWorld / 256.0).toInt()
            val maxTileX = ceil(maxXWorld / 256.0).toInt()
            val minTileY = floor(minYWorld / 256.0).toInt().coerceAtLeast(0)
            val maxTileY = ceil(maxYWorld / 256.0).toInt().coerceAtMost((1 shl intZoom) - 1)

            val maxTiles = 1 shl intZoom

            for (tx in minTileX..maxTileX) {
                val wrappedTx = ((tx % maxTiles) + maxTiles) % maxTiles
                for (ty in minTileY..maxTileY) {
                    val tileScreenX = centerX + (tx * 256f - centerWorld.x.toFloat()) * subZoomScale
                    val tileScreenY = centerY + (ty * 256f - centerWorld.y.toFloat()) * subZoomScale

                    val tileBitmap = tileManager.getTile(
                        zoom = intZoom,
                        x = wrappedTx,
                        y = ty,
                        layer = mapLayer,
                        onTileLoaded = {
                            tileEpoch++
                        },
                    )

                    if (tileBitmap != null) {
                        drawImage(
                            image = tileBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(tileBitmap.width, tileBitmap.height),
                            dstOffset = IntOffset(tileScreenX.toInt(), tileScreenY.toInt()),
                            dstSize = IntSize(tileSize.toInt() + 1, tileSize.toInt() + 1),
                        )
                    } else {
                        // Clean placeholder tile grid
                        drawRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset(tileScreenX, tileScreenY),
                            size = Size(tileSize, tileSize),
                        )
                        drawRect(
                            color = Color(0xFFCBD5E1),
                            topLeft = Offset(tileScreenX, tileScreenY),
                            size = Size(tileSize, tileSize),
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }

            // 2. Draw Route Polyline & Waypoints (if any)
            if (routeWaypoints.isNotEmpty()) {
                val routeOffsets = routeWaypoints.map { wp ->
                    coordinateToScreen(
                        lat = wp.latitude,
                        lng = wp.longitude,
                        centerLat = viewLat,
                        centerLng = viewLng,
                        zoom = zoomLevel,
                        screenWidth = width,
                        screenHeight = height,
                    )
                }

                if (routeOffsets.size >= 2) {
                    val path = Path().apply {
                        moveTo(routeOffsets[0].x, routeOffsets[0].y)
                        for (i in 1 until routeOffsets.size) {
                            lineTo(routeOffsets[i].x, routeOffsets[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF3B82F6),
                        style = Stroke(
                            width = 5f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)),
                        ),
                    )
                }

                routeOffsets.forEachIndexed { idx, pt ->
                    drawCircle(color = Color.White, radius = 9f, center = pt)
                    drawCircle(color = Color(0xFF3B82F6), radius = 6.5f, center = pt)
                }
            }

            // 3. Draw Pin Marker Screen Position (May be uncentered while panning!)
            val pinScreenPos = coordinateToScreen(
                lat = latitude,
                lng = longitude,
                centerLat = viewLat,
                centerLng = viewLng,
                zoom = zoomLevel,
                screenWidth = width,
                screenHeight = height,
            )

            // Draw Accuracy Halo around target Pin
            val metersPerPx = calculateMetersPerPixel(latitude, zoomLevel)
            val accuracyPx = (accuracy.toDouble() / metersPerPx).toFloat().coerceIn(16f, width * 0.45f)

            drawCircle(
                color = if (isMocking) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                radius = accuracyPx,
                center = pinScreenPos,
            )
            drawCircle(
                color = if (isMocking) Color(0xFF10B981).copy(alpha = 0.65f) else Color(0xFFEF4444).copy(alpha = 0.65f),
                radius = accuracyPx,
                center = pinScreenPos,
                style = Stroke(width = 1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
            )

            // Heading cone if bearing is active
            if (isMocking) {
                rotate(degrees = bearing, pivot = pinScreenPos) {
                    val conePath = Path().apply {
                        moveTo(pinScreenPos.x, pinScreenPos.y)
                        lineTo(pinScreenPos.x - 36f, pinScreenPos.y - 95f)
                        lineTo(pinScreenPos.x + 36f, pinScreenPos.y - 95f)
                        close()
                    }
                    drawPath(
                        path = conePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF10B981).copy(alpha = 0.35f), Color.Transparent),
                            startY = pinScreenPos.y - 95f,
                            endY = pinScreenPos.y,
                        ),
                    )
                }

                // Pulsing Emerald Mock Ring
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = pulseRingAlpha),
                    radius = pulseRingRadius,
                    center = pinScreenPos,
                    style = Stroke(width = 2.5f),
                )
            }

            // 4. Draw Custom Teardrop Pin Marker
            drawTeardropPin(
                pinPos = pinScreenPos,
                isMocking = isMocking,
            )

            // 5. Scale Bar Indicator (Bottom Left)
            drawOsmScaleBar(
                metersPerPixel = metersPerPx,
            )
        }

        // Floating Zoom Controls (+ / -)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(
                onClick = { zoomLevel = (zoomLevel + 1f).coerceAtMost(19.0f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(22.dp))
            }

            FilledIconButton(
                onClick = { zoomLevel = (zoomLevel - 1f).coerceAtLeast(2.0f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(22.dp))
            }
        }

        // Compass Rose (Top Left below search)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 68.dp, start = 14.dp)
                .size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "North Compass",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(bearing),
                )
            }
        }

        // Live OSM Tile & Zoom HUD (Top Center)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "OSM Zoom: ${String.format(Locale.US, "%.1fx", zoomLevel)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${String.format(Locale.US, "%.4f", viewLat)}°, ${String.format(Locale.US, "%.4f", viewLng)}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }

        // OpenStreetMap Attribution (Bottom Left)
        Surface(
            shape = RoundedCornerShape(topEnd = 6.dp),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4B5563),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

// ======================== CANVAS DRAWING HELPERS ========================

private fun DrawScope.drawTeardropPin(
    pinPos: Offset,
    isMocking: Boolean,
) {
    val centerX = pinPos.x
    val centerY = pinPos.y

    // Drop shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.32f),
        topLeft = Offset(centerX - 9f, centerY - 3f),
        size = Size(18f, 7f),
    )

    val primaryColor = if (isMocking) Color(0xFF10B981) else Color(0xFFEF4444)
    val darkColor = if (isMocking) Color(0xFF047857) else Color(0xFFB91C1C)

    val pinHeight = 38f
    val pinRadius = 15f
    val pinTopY = centerY - pinHeight

    val path = Path().apply {
        moveTo(centerX, centerY)
        cubicTo(
            centerX - 6f, centerY - 10f,
            centerX - pinRadius, pinTopY + pinRadius + 4f,
            centerX - pinRadius, pinTopY + pinRadius,
        )
        arcTo(
            rect = Rect(
                centerX - pinRadius, pinTopY,
                centerX + pinRadius, pinTopY + pinRadius * 2,
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        cubicTo(
            centerX + pinRadius, pinTopY + pinRadius + 4f,
            centerX + 6f, centerY - 10f,
            centerX, centerY,
        )
        close()
    }

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(primaryColor, darkColor),
            startY = pinTopY,
            endY = centerY,
        ),
    )

    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(width = 2.5f),
    )

    drawCircle(
        color = Color.White,
        radius = 5.5f,
        center = Offset(centerX, pinTopY + pinRadius),
    )
}

private fun DrawScope.drawOsmScaleBar(metersPerPixel: Double) {
    val barWidthPx = 110f
    val distanceMeters = barWidthPx * metersPerPixel
    val label = if (distanceMeters >= 1000.0) {
        String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    } else {
        String.format(Locale.US, "%d m", distanceMeters.toInt())
    }

    val startX = 20f
    val startY = size.height - 28f

    drawLine(
        color = Color(0xFF374151),
        start = Offset(startX, startY),
        end = Offset(startX + barWidthPx, startY),
        strokeWidth = 3f,
    )
    drawLine(
        color = Color(0xFF374151),
        start = Offset(startX, startY - 6f),
        end = Offset(startX, startY + 6f),
        strokeWidth = 2.5f,
    )
    drawLine(
        color = Color(0xFF374151),
        start = Offset(startX + barWidthPx, startY - 6f),
        end = Offset(startX + barWidthPx, startY + 6f),
        strokeWidth = 2.5f,
    )
}

// ======================== SLIPPY MAP MERCATOR PROJECTION MATH ========================

private data class WorldPoint(val x: Double, val y: Double)

private fun coordinateToWorld(lat: Double, lng: Double, zoom: Int): WorldPoint {
    val n = 1 shl zoom
    val x = ((lng + 180.0) / 360.0) * (256.0 * n)
    val latRad = Math.toRadians(lat)
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (256.0 * n)
    return WorldPoint(x, y)
}

private fun coordinateToScreen(
    lat: Double,
    lng: Double,
    centerLat: Double,
    centerLng: Double,
    zoom: Float,
    screenWidth: Float,
    screenHeight: Float,
): Offset {
    val intZoom = zoom.toInt().coerceIn(1, 19)
    val subZoomScale = 2.0.pow((zoom - intZoom).toDouble()).toFloat()

    val ptWorld = coordinateToWorld(lat, lng, intZoom)
    val centerWorld = coordinateToWorld(centerLat, centerLng, intZoom)

    val screenX = (screenWidth / 2f) + ((ptWorld.x - centerWorld.x).toFloat() * subZoomScale)
    val screenY = (screenHeight / 2f) + ((ptWorld.y - centerWorld.y).toFloat() * subZoomScale)

    return Offset(screenX, screenY)
}

private fun screenToCoordinate(
    screenOffset: Offset,
    centerLat: Double,
    centerLng: Double,
    zoom: Float,
    screenWidth: Float,
    screenHeight: Float,
): MockCoordinate {
    val intZoom = zoom.toInt().coerceIn(1, 19)
    val subZoomScale = 2.0.pow((zoom - intZoom).toDouble()).toFloat()

    val centerWorld = coordinateToWorld(centerLat, centerLng, intZoom)
    val dx = (screenOffset.x - (screenWidth / 2f)) / subZoomScale
    val dy = (screenOffset.y - (screenHeight / 2f)) / subZoomScale

    val targetWorldX = centerWorld.x + dx
    val targetWorldY = centerWorld.y + dy

    val totalWorldSize = 256.0 * (1 shl intZoom)
    val targetLng = (targetWorldX / totalWorldSize) * 360.0 - 180.0

    val n = PI - 2.0 * PI * (targetWorldY / totalWorldSize)
    val targetLat = Math.toDegrees(atan(sinh(n)))

    return MockCoordinate(
        latitude = targetLat.coerceIn(-85.0, 85.0),
        longitude = ((targetLng + 180.0) % 360.0) - 180.0,
    )
}

private fun calculateMetersPerPixel(latitude: Double, zoom: Float): Double {
    val earthCircumference = 40075016.686
    val rad = Math.toRadians(latitude)
    return (earthCircumference * cos(rad)) / (256.0 * 2.0.pow(zoom.toDouble()))
}

// ======================== HIGH-PERFORMANCE OSM TILE MANAGER ========================

class OsmTileManager private constructor(context: Context) {
    private val memoryCache: LruCache<String, ImageBitmap>
    private val diskCacheDir: File = File(context.cacheDir, "osmtiles").apply { mkdirs() }
    private val activeDownloads = mutableSetOf<String>()

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // 1/8th of available memory for tile cache
        memoryCache = object : LruCache<String, ImageBitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: ImageBitmap): Int {
                return (bitmap.width * bitmap.height * 4) / 1024
            }
        }
    }

    fun getTile(
        zoom: Int,
        x: Int,
        y: Int,
        layer: String,
        onTileLoaded: () -> Unit,
    ): ImageBitmap? {
        val key = "$layer/$zoom/$x/$y"

        // 1. Memory Cache Hit
        val cached = memoryCache.get(key)
        if (cached != null) return cached

        // 2. Asynchronous Disk / Network fetch
        synchronized(activeDownloads) {
            if (activeDownloads.contains(key)) return null
            activeDownloads.add(key)
        }

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // Check Disk Cache
                val diskFile = File(diskCacheDir, "$layer-$zoom-$x-$y.png")
                var bitmap: Bitmap? = if (diskFile.exists() && diskFile.length() > 0) {
                    BitmapFactory.decodeFile(diskFile.absolutePath)
                } else {
                    null
                }

                // Download from OpenStreetMap / CartoDB tile server
                if (bitmap == null) {
                    val tileUrl = getTileUrl(zoom, x, y, layer)
                    val conn = URL(tileUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 7000
                    conn.setRequestProperty("User-Agent", "GetoGpsMock/1.0 (Android; littlesight9@gmail.com)")
                    conn.doInput = true
                    conn.connect()

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val input = conn.inputStream
                        val bytes = input.readBytes()
                        input.close()
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                        // Save to disk cache
                        try {
                            FileOutputStream(diskFile).use { it.write(bytes) }
                        } catch (_: Exception) {}
                    }
                    conn.disconnect()
                }

                if (bitmap != null) {
                    val imageBitmap = bitmap.asImageBitmap()
                    memoryCache.put(key, imageBitmap)
                    withContext(Dispatchers.Main) {
                        onTileLoaded()
                    }
                }
            } catch (_: Exception) {
                // Ignore transient network errors
            } finally {
                synchronized(activeDownloads) {
                    activeDownloads.remove(key)
                }
            }
        }

        return null
    }

    private fun getTileUrl(zoom: Int, x: Int, y: Int, layer: String): String {
        val subdomains = listOf("a", "b", "c")
        val s = subdomains[(x + y).absoluteValue % 3]

        return when (layer) {
            "dark" -> "https://basemaps.cartocdn.com/dark_all/$zoom/$x/$y.png"
            "satellite" -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
            else -> "https://$s.tile.openstreetmap.org/$zoom/$x/$y.png"
        }
    }

    companion object {
        @Volatile
        private var instance: OsmTileManager? = null

        fun getInstance(context: Context): OsmTileManager {
            return instance ?: synchronized(this) {
                instance ?: OsmTileManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
