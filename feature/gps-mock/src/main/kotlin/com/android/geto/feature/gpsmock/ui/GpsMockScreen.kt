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
package com.android.geto.feature.gpsmock.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.geto.feature.gpsmock.GpsMockUiState
import com.android.geto.feature.gpsmock.GpsMockViewModel
import com.android.geto.feature.gpsmock.ui.components.DeveloperOptionsGuideDialog
import com.android.geto.feature.gpsmock.ui.components.FavoritesBottomSheet
import com.android.geto.feature.gpsmock.ui.components.GpsMockMapView
import com.android.geto.feature.gpsmock.ui.components.GpsMockSettingsDialog
import com.android.geto.feature.gpsmock.ui.components.JoystickOverlay
import com.android.geto.feature.gpsmock.ui.components.ManualCoordinateDialog
import com.android.geto.feature.gpsmock.ui.components.RoutePlannerDialog
import com.android.geto.feature.gpsmock.ui.components.SaveFavoriteDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.Locale

@Composable
fun GpsMockRoute(
    modifier: Modifier = Modifier,
    viewModel: GpsMockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GpsMockScreen(
        modifier = modifier,
        uiState = uiState,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GpsMockScreen(
    modifier: Modifier = Modifier,
    uiState: GpsMockUiState,
    viewModel: GpsMockViewModel,
) {
    val context = LocalContext.current
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Fullscreen OpenStreetMap / Tile Engine
        GpsMockMapView(
            latitude = if (uiState.isMocking) uiState.currentLatitude else uiState.targetLatitude,
            longitude = if (uiState.isMocking) uiState.currentLongitude else uiState.targetLongitude,
            bearing = uiState.bearing,
            accuracy = uiState.accuracy,
            isMocking = uiState.isMocking,
            mapLayer = uiState.mapLayer,
            routeWaypoints = uiState.activeRouteWaypoints,
            forceCenterTrigger = uiState.centerMapTrigger,
            onLocationSelected = { lat, lng ->
                viewModel.setTargetLocation(lat, lng)
            },
        )

        // 1. TOP FLOATING SEARCH BAR (Hides when controls are disappeared)
        AnimatedVisibility(
            visible = !uiState.isUiHidden,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text("Search location, city, landmark...", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = viewModel::clearSearch,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Autocomplete Dropdown
                AnimatedVisibility(
                    visible = uiState.searchResults.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .heightIn(max = 200.dp),
                    ) {
                        LazyColumn {
                            items(uiState.searchResults) { result ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectSearchResult(result) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationSearching,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = result.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (result.type.isNotBlank()) {
                                            Text(
                                                text = result.type.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. DISAPPEAR / IMMERSIVE MODE TOGGLE RESTORER (Visible when UI is hidden)
        AnimatedVisibility(
            visible = uiState.isUiHidden,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { viewModel.setHideUi(false) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Show Controls",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Show Controls",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 3. RIGHT FLOATING ACTION TOOLBAR (Compact, centered vertically, no overlap)
        AnimatedVisibility(
            visible = !uiState.isUiHidden,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    // Button: Disappear / Hide UI Controls (Like real mock GPS app)
                    MapToolButton(
                        icon = Icons.Default.VisibilityOff,
                        contentDescription = "Hide UI Controls",
                        onClick = viewModel::toggleHideUi,
                        highlighted = false,
                    )

                    // Button: Recenter Map on Pin / Target
                    MapToolButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "Center on Pin",
                        onClick = {
                            viewModel.centerMapOnPin()
                            Toast.makeText(context, "Centered on pin location", Toast.LENGTH_SHORT).show()
                        },
                        highlighted = false,
                    )

                    // Button: Map Layer Switcher
                    MapToolButton(
                        icon = Icons.Default.Layers,
                        contentDescription = "Map Layers",
                        onClick = {
                            val nextLayer = when (uiState.mapLayer) {
                                "streets" -> "dark"
                                "dark" -> "satellite"
                                else -> "streets"
                            }
                            viewModel.setMapLayer(nextLayer)
                        },
                        highlighted = false,
                    )

                    // Button: Joystick Floating Controller
                    MapToolButton(
                        icon = Icons.Default.ControlCamera,
                        contentDescription = "Toggle Joystick",
                        onClick = viewModel::toggleJoystick,
                        highlighted = uiState.isJoystickVisible,
                    )

                    // Button: Exact Manual Coordinates
                    MapToolButton(
                        icon = Icons.Default.EditLocationAlt,
                        contentDescription = "Set Coordinates",
                        onClick = { viewModel.showManualCoordinatesDialog(true) },
                        highlighted = false,
                    )

                    // Button: Route Simulation
                    MapToolButton(
                        icon = Icons.Default.AltRoute,
                        contentDescription = "Route Simulation",
                        onClick = { viewModel.showRoutePlannerDialog(true) },
                        highlighted = uiState.isRouteSimulating,
                    )

                    // Button: Saved Locations / Bookmarks
                    MapToolButton(
                        icon = Icons.Default.Favorite,
                        contentDescription = "Saved Locations",
                        onClick = { viewModel.showFavoritesBottomSheet(true) },
                        highlighted = false,
                    )

                    // Button: GPS Settings & Dev Options
                    MapToolButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "GPS Settings",
                        onClick = { viewModel.showSettingsDialog(true) },
                        highlighted = false,
                    )
                }
            }
        }

        // 4. FLOATING JOYSTICK CONTROLLER (Draggable & Collapsible)
        JoystickOverlay(
            visible = uiState.isJoystickVisible,
            onMove = viewModel::moveWithJoystick,
            onClose = viewModel::toggleJoystick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
        )

        // 5. UNIFIED BOTTOM CONTROL DECK (Non-overlapping card + Real Mock FAB)
        AnimatedVisibility(
            visible = !uiState.isUiHidden,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Warning Banner for Developer Options if not granted
                if (uiState.hasMockPermissionError) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Set Geto as Mock location app in Dev Options",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp,
                            )
                            TextButton(onClick = { viewModel.showDeveloperOptionsGuide(true) }) {
                                Text("Enable", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Row with Location Info Surface on left/center & Authentic Mock FAB on right
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Location Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            // Header Row (Status dot, name, coordinates, action icons)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = viewModel::toggleBottomCardExpanded,
                                    ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .scale(if (uiState.isMocking && !uiState.isPaused) pulseScale else 1f)
                                            .background(
                                                color = when {
                                                    uiState.isMocking && !uiState.isPaused -> Color(0xFF00C853)
                                                    uiState.isMocking && uiState.isPaused -> Color(0xFFF59E0B)
                                                    else -> MaterialTheme.colorScheme.outline
                                                },
                                                shape = CircleShape,
                                            ),
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Text(
                                            text = uiState.locationName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.5f", if (uiState.isMocking) uiState.currentLatitude else uiState.targetLatitude)}, ${String.format(Locale.US, "%.5f", if (uiState.isMocking) uiState.currentLongitude else uiState.targetLongitude)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Coordinates", "${uiState.targetLatitude}, ${uiState.targetLongitude}")
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Coordinates copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp))
                                    }

                                    IconButton(
                                        onClick = { viewModel.showSaveFavoriteDialog(true) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Save", modifier = Modifier.size(17.dp))
                                    }

                                    IconButton(
                                        onClick = viewModel::toggleBottomCardExpanded,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isBottomCardExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (uiState.isBottomCardExpanded) "Collapse" else "Expand",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            // Expanded Telemetry and Speed Multipliers
                            if (uiState.isBottomCardExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                    ) {
                                        MiniStatItem("Altitude", "${uiState.altitude.toInt()}m")
                                        MiniStatItem("Speed", "${uiState.speedKmh.toInt()} km/h")
                                        MiniStatItem("Bearing", "${uiState.bearing.toInt()}°")
                                        MiniStatItem("Accuracy", "${uiState.accuracy.toInt()}m")
                                    }
                                }

                                if (uiState.isRouteSimulating) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    listOf(
                                        "5 km/h" to 5f,
                                        "15 km/h" to 15f,
                                        "30 km/h" to 30f,
                                        "60 km/h" to 60f,
                                        "120 km/h" to 120f,
                                    ).forEach { (label, speed) ->
                                        AssistChip(
                                            onClick = { viewModel.setSpeed(speed) },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (uiState.speedKmh == speed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(26.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Authentic GPS Mock Action FAB (Play / Stop Button with glow & pulse)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (uiState.isMocking) {
                            // Secondary Pause / Resume FAB
                            SmallFloatingActionButton(
                                onClick = viewModel::togglePause,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                                shape = CircleShape,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (uiState.isPaused) "Resume" else "Pause",
                                    tint = if (uiState.isPaused) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Crimson Red Stop Button
                            FloatingActionButton(
                                onClick = viewModel::stopMocking,
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape, spotColor = Color(0xFFE53935)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Mocking",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        } else {
                            // Authentic Emerald Green Play Button
                            FloatingActionButton(
                                onClick = viewModel::startMocking,
                                containerColor = Color(0xFF00C853),
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(8.dp, CircleShape, spotColor = Color(0xFF00C853)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Mocking",
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modals & Dialogs
    if (uiState.showManualCoordinateDialog) {
        ManualCoordinateDialog(
            initialLatitude = uiState.targetLatitude,
            initialLongitude = uiState.targetLongitude,
            initialAltitude = uiState.altitude,
            initialSpeed = uiState.speedKmh,
            onConfirm = { lat, lng, alt, speed ->
                viewModel.setTargetLocation(lat, lng)
                viewModel.setAltitude(alt)
                viewModel.setSpeed(speed)
                viewModel.centerMapOnPin()
                viewModel.showManualCoordinatesDialog(false)
            },
            onDismiss = { viewModel.showManualCoordinatesDialog(false) },
        )
    }

    if (uiState.showSaveFavoriteDialog) {
        SaveFavoriteDialog(
            latitude = uiState.targetLatitude,
            longitude = uiState.targetLongitude,
            initialName = uiState.locationName,
            onSave = { name, tag ->
                viewModel.saveFavorite(name, tag)
                Toast.makeText(context, "Location saved to favorites!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.showSaveFavoriteDialog(false) },
        )
    }

    if (uiState.showFavoritesBottomSheet) {
        FavoritesBottomSheet(
            favorites = uiState.favorites,
            history = uiState.history,
            onSelectLocation = viewModel::teleportToLocation,
            onDeleteFavorite = viewModel::deleteFavorite,
            onDismiss = { viewModel.showFavoritesBottomSheet(false) },
        )
    }

    if (uiState.showRoutePlannerDialog) {
        RoutePlannerDialog(
            currentLat = uiState.targetLatitude,
            currentLng = uiState.targetLongitude,
            savedRoutes = uiState.routes,
            onStartRoute = viewModel::startRouteSimulation,
            onSaveRoute = viewModel::saveCustomRoute,
            onDeleteRoute = viewModel::deleteCustomRoute,
            onDismiss = { viewModel.showRoutePlannerDialog(false) },
        )
    }

    if (uiState.showSettingsDialog) {
        GpsMockSettingsDialog(
            currentSettings = uiState.settings,
            onSave = viewModel::updateSettings,
            onOpenDeveloperOptions = viewModel::openDeveloperOptions,
            onDismiss = { viewModel.showSettingsDialog(false) },
        )
    }

    if (uiState.isDeveloperOptionsGuideVisible) {
        DeveloperOptionsGuideDialog(
            onOpenDeveloperOptions = viewModel::openDeveloperOptions,
            onDismiss = { viewModel.showDeveloperOptionsGuide(false) },
        )
    }
}

@Composable
private fun MapToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean,
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
        shape = CircleShape,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MiniStatItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
        )
    }
}
