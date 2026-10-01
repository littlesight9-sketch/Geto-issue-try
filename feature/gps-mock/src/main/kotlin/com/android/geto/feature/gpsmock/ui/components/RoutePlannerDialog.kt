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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.geto.feature.gpsmock.model.MockCoordinate
import com.android.geto.feature.gpsmock.model.MockRoute
import com.android.geto.feature.gpsmock.model.RouteLoopMode
import java.util.Locale
import java.util.UUID

@Composable
fun RoutePlannerDialog(
    currentLat: Double,
    currentLng: Double,
    savedRoutes: List<MockRoute>,
    onStartRoute: (MockRoute) -> Unit,
    onSaveRoute: (MockRoute) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var routeName by remember { mutableStateOf("Custom Route") }
    var speedKmh by remember { mutableFloatStateOf(15f) }
    var loopMode by remember { mutableStateOf(RouteLoopMode.REPEAT) }
    var waypoints by remember {
        mutableStateOf(
            listOf(
                MockCoordinate(currentLat, currentLng),
                MockCoordinate(currentLat + 0.005, currentLng + 0.005),
                MockCoordinate(currentLat + 0.005, currentLng - 0.005),
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate GPS Route", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Waypoints (${waypoints.size} points)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                ) {
                    items(waypoints.mapIndexed { idx, coord -> idx to coord }) { (idx, coord) ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "#${idx + 1}: ${String.format(Locale.US, "%.4f, %.4f", coord.latitude, coord.longitude)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (waypoints.size > 2) {
                                    IconButton(
                                        onClick = {
                                            waypoints = waypoints.toMutableList().apply { removeAt(idx) }
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val last = waypoints.lastOrNull() ?: MockCoordinate(currentLat, currentLng)
                        waypoints = waypoints + MockCoordinate(last.latitude + 0.002, last.longitude + 0.002)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Waypoint Near Current")
                }

                Text(
                    text = "Speed: ${speedKmh.toInt()} km/h",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = speedKmh,
                    onValueChange = { speedKmh = it },
                    valueRange = 2f..120f,
                )

                Text(
                    text = "Loop Mode",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteLoopMode.entries.forEach { mode ->
                        FilterChip(
                            selected = loopMode == mode,
                            onClick = { loopMode = mode },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val route = MockRoute(
                        id = UUID.randomUUID().toString(),
                        name = routeName,
                        waypoints = waypoints,
                        speedKmh = speedKmh,
                        loopMode = loopMode,
                        isSimulating = true,
                    )
                    onStartRoute(route)
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Simulation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
