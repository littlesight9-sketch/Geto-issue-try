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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun JoystickOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onMove: (angleDegrees: Double, distanceMeters: Double) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        var widgetOffset by remember { mutableStateOf(Offset.Zero) }
        var isMinimized by remember { mutableStateOf(false) }
        var stepMultiplier by remember { mutableFloatStateOf(5.0f) }
        var thumbOffset by remember { mutableStateOf(Offset.Zero) }
        val maxRadius = 50f

        Surface(
            shape = if (isMinimized) CircleShape else RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .offset { IntOffset(widgetOffset.x.roundToInt(), widgetOffset.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        widgetOffset += dragAmount
                    }
                }
                .animateContentSize(),
        ) {
            if (isMinimized) {
                // Minimized floating circular badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { isMinimized = false },
                ) {
                    Icon(
                        imageVector = Icons.Default.ControlCamera,
                        contentDescription = "Expand Joystick",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                // Expanded complete Joystick controller
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    // Header Bar with Drag handle, title, minimize & close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.size(width = 145.dp, height = 28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag Joystick",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Joystick",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row {
                            IconButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier.size(22.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Minimize",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(22.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Joystick",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    // Speed Multiplier Step Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            "3m" to 3f,
                            "8m" to 8f,
                            "20m" to 20f,
                            "50m" to 50f,
                        ).forEach { (label, step) ->
                            FilterChip(
                                selected = stepMultiplier == step,
                                onClick = { stepMultiplier = step },
                                label = { Text(label, fontSize = 9.sp) },
                                modifier = Modifier.size(width = 32.dp, height = 22.dp),
                            )
                        }
                    }

                    // Analog Touch Pad
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                ),
                            )
                            .pointerInput(stepMultiplier) {
                                detectDragGestures(
                                    onDragEnd = { thumbOffset = Offset.Zero },
                                    onDragCancel = { thumbOffset = Offset.Zero },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val newOffset = thumbOffset + dragAmount
                                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                                        thumbOffset = if (distance <= maxRadius) {
                                            newOffset
                                        } else {
                                            val angle = atan2(newOffset.y, newOffset.x)
                                            Offset(
                                                (kotlin.math.cos(angle) * maxRadius).toFloat(),
                                                (kotlin.math.sin(angle) * maxRadius).toFloat(),
                                            )
                                        }

                                        if (distance > 6f) {
                                            val angleRad = atan2(thumbOffset.x, -thumbOffset.y)
                                            val bearingDeg = ((Math.toDegrees(angleRad.toDouble()) + 360) % 360)
                                            val stepRatio = (distance.coerceAtMost(maxRadius) / maxRadius)
                                            onMove(bearingDeg, stepMultiplier.toDouble() * stepRatio)
                                        }
                                    },
                                )
                            },
                    ) {
                        // Directional labels
                        Text(
                            text = "N",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 2.dp),
                        )
                        Text(
                            text = "S",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp),
                        )
                        Text(
                            text = "W",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 3.dp),
                        )
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 3.dp),
                        )

                        // 4 directional step tap buttons
                        IconButton(
                            onClick = { onMove(0.0, stepMultiplier.toDouble()) },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .size(22.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "North", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onMove(180.0, stepMultiplier.toDouble()) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .size(22.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "South", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onMove(270.0, stepMultiplier.toDouble()) },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp)
                                .size(22.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "West", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onMove(90.0, stepMultiplier.toDouble()) },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                                .size(22.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "East", modifier = Modifier.size(16.dp))
                        }

                        // Draggable thumb stick
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                                .size(36.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary,
                                        ),
                                    ),
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(
                                        if (thumbOffset != Offset.Zero) {
                                            ((Math.toDegrees(atan2(thumbOffset.x, -thumbOffset.y).toDouble()) + 360) % 360).toFloat()
                                        } else 0f,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
