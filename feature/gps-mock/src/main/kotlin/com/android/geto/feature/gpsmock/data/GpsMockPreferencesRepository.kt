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
package com.android.geto.feature.gpsmock.data

import android.content.Context
import android.content.SharedPreferences
import com.android.geto.feature.gpsmock.manager.MockLocationManager
import com.android.geto.feature.gpsmock.model.GpsMockSettings
import com.android.geto.feature.gpsmock.model.MockCoordinate
import com.android.geto.feature.gpsmock.model.MockRoute
import com.android.geto.feature.gpsmock.model.SavedLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "gps_mock_prefs"
private const val KEY_FAVORITES = "favorites_json"
private const val KEY_HISTORY = "history_json"
private const val KEY_ROUTES = "routes_json"
private const val KEY_SETTINGS = "settings_json"
private const val KEY_LAST_LAT = "last_latitude"
private const val KEY_LAST_LNG = "last_longitude"

@Singleton
class GpsMockPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _favorites = MutableStateFlow<List<SavedLocation>>(loadFavorites())
    val favorites = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<SavedLocation>>(loadHistory())
    val history = _history.asStateFlow()

    private val _routes = MutableStateFlow<List<MockRoute>>(loadRoutes())
    val routes = _routes.asStateFlow()

    private val _settings = MutableStateFlow<GpsMockSettings>(loadSettings())
    val settings = _settings.asStateFlow()

    fun getLastLocation(): MockCoordinate {
        val lat = prefs.getFloat(KEY_LAST_LAT, 37.7749f).toDouble() // Default San Francisco
        val lng = prefs.getFloat(KEY_LAST_LNG, -122.4194f).toDouble()
        return MockCoordinate(lat, lng)
    }

    fun saveLastLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LNG, lng.toFloat())
            .apply()
    }

    private fun loadFavorites(): List<SavedLocation> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return defaultFavorites()
        return try {
            json.decodeFromString<List<SavedLocation>>(raw)
        } catch (_: Exception) {
            defaultFavorites()
        }
    }

    private fun loadHistory(): List<SavedLocation> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SavedLocation>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadRoutes(): List<MockRoute> {
        val raw = prefs.getString(KEY_ROUTES, null) ?: return defaultRoutes()
        return try {
            json.decodeFromString<List<MockRoute>>(raw)
        } catch (_: Exception) {
            defaultRoutes()
        }
    }

    private fun loadSettings(): GpsMockSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return GpsMockSettings()
        return try {
            json.decodeFromString<GpsMockSettings>(raw)
        } catch (_: Exception) {
            GpsMockSettings()
        }
    }

    fun addFavorite(location: SavedLocation) {
        val updated = (_favorites.value.filter { it.id != location.id } + location).sortedByDescending { it.timestamp }
        _favorites.update { updated }
        persistFavorites(updated)
    }

    fun deleteFavorite(id: String) {
        val updated = _favorites.value.filter { it.id != id }
        _favorites.update { updated }
        persistFavorites(updated)
    }

    fun addHistory(location: SavedLocation) {
        val list = _history.value.toMutableList()
        list.removeAll { MockLocationManager.calculateDistance(it.latitude, it.longitude, location.latitude, location.longitude) < 50 }
        list.add(0, location)
        val trimmed = list.take(30)
        _history.update { trimmed }
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(trimmed)).apply()
    }

    fun clearHistory() {
        _history.update { emptyList() }
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun saveRoute(route: MockRoute) {
        val updated = (_routes.value.filter { it.id != route.id } + route)
        _routes.update { updated }
        prefs.edit().putString(KEY_ROUTES, json.encodeToString(updated)).apply()
    }

    fun deleteRoute(id: String) {
        val updated = _routes.value.filter { it.id != id }
        _routes.update { updated }
        prefs.edit().putString(KEY_ROUTES, json.encodeToString(updated)).apply()
    }

    fun updateSettings(settings: GpsMockSettings) {
        _settings.update { settings }
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings)).apply()
    }

    private fun persistFavorites(list: List<SavedLocation>) {
        prefs.edit().putString(KEY_FAVORITES, json.encodeToString(list)).apply()
    }

    private fun defaultFavorites(): List<SavedLocation> = listOf(
        SavedLocation(
            id = "fav_eiffel",
            name = "Eiffel Tower, Paris",
            latitude = 48.8584,
            longitude = 2.2945,
            tag = "Travel",
        ),
        SavedLocation(
            id = "fav_times_square",
            name = "Times Square, New York",
            latitude = 40.7580,
            longitude = -73.9855,
            tag = "Travel",
        ),
        SavedLocation(
            id = "fav_shibuya",
            name = "Shibuya Crossing, Tokyo",
            latitude = 35.6595,
            longitude = 139.7005,
            tag = "Travel",
        ),
        SavedLocation(
            id = "fav_sydney_opera",
            name = "Sydney Opera House, Australia",
            latitude = -33.8568,
            longitude = 151.2153,
            tag = "Travel",
        ),
        SavedLocation(
            id = "fav_colosseum",
            name = "Colosseum, Rome",
            latitude = 41.8902,
            longitude = 12.4922,
            tag = "Travel",
        ),
    )

    private fun defaultRoutes(): List<MockRoute> = listOf(
        MockRoute(
            id = "route_central_park",
            name = "Central Park Loop",
            waypoints = listOf(
                MockCoordinate(40.7681, -73.9819),
                MockCoordinate(40.7781, -73.9749),
                MockCoordinate(40.7851, -73.9689),
                MockCoordinate(40.7961, -73.9589),
                MockCoordinate(40.7891, -73.9529),
                MockCoordinate(40.7711, -73.9659),
            ),
            speedKmh = 12f,
        ),
        MockRoute(
            id = "route_seine_walk",
            name = "Seine River Stroll",
            waypoints = listOf(
                MockCoordinate(48.8584, 2.2945),
                MockCoordinate(48.8606, 2.3126),
                MockCoordinate(48.8622, 2.3364),
                MockCoordinate(48.8530, 2.3499),
            ),
            speedKmh = 5f,
        ),
    )
}
