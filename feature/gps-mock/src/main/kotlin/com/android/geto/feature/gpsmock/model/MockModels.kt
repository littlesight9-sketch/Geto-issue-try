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
package com.android.geto.feature.gpsmock.model

import kotlinx.serialization.Serializable

@Serializable
data class MockCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
)

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val tag: String = "Favorite",
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class MockRoute(
    val id: String,
    val name: String,
    val waypoints: List<MockCoordinate>,
    val speedKmh: Float = 15f,
    val loopMode: RouteLoopMode = RouteLoopMode.REPEAT,
    val isSimulating: Boolean = false,
)

@Serializable
enum class RouteLoopMode {
    REPEAT,
    REVERSE,
    ONCE,
}

enum class SpeedPreset(val label: String, val speedKmh: Float) {
    WALKING("Walk (5 km/h)", 5f),
    JOGGING("Jog (10 km/h)", 10f),
    CYCLING("Bike (20 km/h)", 20f),
    DRIVING("Drive (60 km/h)", 60f),
    FAST("Speed (120 km/h)", 120f),
}

@Serializable
data class SearchResultPlace(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "",
)

@Serializable
data class GpsMockSettings(
    val jitterEnabled: Boolean = false,
    val jitterRadiusMeters: Float = 1.5f,
    val updateIntervalMs: Long = 1000L,
    val defaultSpeedKmh: Float = 10f,
    val mapLayer: String = "streets",
)
