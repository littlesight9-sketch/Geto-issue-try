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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ManualCoordinateDialog(
    initialLatitude: Double,
    initialLongitude: Double,
    initialAltitude: Double,
    initialSpeed: Float,
    onConfirm: (lat: Double, lng: Double, alt: Double, speed: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var latText by remember { mutableStateOf(initialLatitude.toString()) }
    var lngText by remember { mutableStateOf(initialLongitude.toString()) }
    var altText by remember { mutableStateOf(initialAltitude.toString()) }
    var speedText by remember { mutableStateOf(initialSpeed.toString()) }

    var latError by remember { mutableStateOf<String?>(null) }
    var lngError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enter Custom Coordinates",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        latError = null
                    },
                    label = { Text("Latitude (-90 to +90)") },
                    isError = latError != null,
                    supportingText = latError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it
                        lngError = null
                    },
                    label = { Text("Longitude (-180 to +180)") },
                    isError = lngError != null,
                    supportingText = lngError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = altText,
                        onValueChange = { altText = it },
                        label = { Text("Altitude (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )

                    OutlinedTextField(
                        value = speedText,
                        onValueChange = { speedText = it },
                        label = { Text("Speed (km/h)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lng = lngText.toDoubleOrNull()
                    val alt = altText.toDoubleOrNull() ?: 15.0
                    val speed = speedText.toFloatOrNull() ?: 10.0f

                    if (lat == null || lat < -90.0 || lat > 90.0) {
                        latError = "Invalid latitude (-90 to 90)"
                        return@Button
                    }
                    if (lng == null || lng < -180.0 || lng > 180.0) {
                        lngError = "Invalid longitude (-180 to 180)"
                        return@Button
                    }

                    onConfirm(lat, lng, alt, speed)
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Set Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
