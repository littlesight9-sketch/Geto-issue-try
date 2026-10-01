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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.geto.feature.gpsmock.model.GpsMockSettings

@Composable
fun GpsMockSettingsDialog(
    currentSettings: GpsMockSettings,
    onSave: (GpsMockSettings) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onDismiss: () -> Unit,
) {
    var jitterEnabled by remember { mutableStateOf(currentSettings.jitterEnabled) }
    var updateIntervalMs by remember { mutableLongStateOf(currentSettings.updateIntervalMs) }
    var mapLayer by remember { mutableStateOf(currentSettings.mapLayer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GPS Mock Settings", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Jitter / Drift
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Realistic GPS Drift / Jitter", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Adds natural micro-fluctuations (±1.5m) to prevent static spoofing detection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = jitterEnabled,
                        onCheckedChange = { jitterEnabled = it },
                    )
                }

                // Update Interval
                Text("Update Frequency", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(
                        "250ms" to 250L,
                        "500ms" to 500L,
                        "1s" to 1000L,
                        "2s" to 2000L,
                    ).forEach { (label, interval) ->
                        FilterChip(
                            selected = updateIntervalMs == interval,
                            onClick = { updateIntervalMs = interval },
                            label = { Text(label) },
                        )
                    }
                }

                // Map Layer
                Text("Default Map Layer", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(
                        "streets" to "Street",
                        "dark" to "Dark",
                        "satellite" to "Satellite",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = mapLayer == key,
                            onClick = { mapLayer = key },
                            label = { Text(label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onOpenDeveloperOptions,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Developer Options")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentSettings.copy(
                            jitterEnabled = jitterEnabled,
                            updateIntervalMs = updateIntervalMs,
                            mapLayer = mapLayer,
                        ),
                    )
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
