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
package com.android.geto.feature.gpsmock

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.geto.feature.gpsmock.data.GpsMockPreferencesRepository
import com.android.geto.feature.gpsmock.manager.MockLocationManager
import com.android.geto.feature.gpsmock.model.GpsMockSettings
import com.android.geto.feature.gpsmock.model.MockCoordinate
import com.android.geto.feature.gpsmock.model.MockRoute
import com.android.geto.feature.gpsmock.model.RouteLoopMode
import com.android.geto.feature.gpsmock.model.SavedLocation
import com.android.geto.feature.gpsmock.model.SearchResultPlace
import com.android.geto.feature.gpsmock.service.MockLocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject

private const val TAG = "GpsMockViewModel"

@HiltViewModel
class GpsMockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: GpsMockPreferencesRepository,
    val mockLocationManager: MockLocationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GpsMockUiState())
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var reverseGeocodeJob: Job? = null

    init {
        val lastLocation = preferencesRepository.getLastLocation()
        _uiState.update {
            it.copy(
                targetLatitude = lastLocation.latitude,
                targetLongitude = lastLocation.longitude,
                currentLatitude = lastLocation.latitude,
                currentLongitude = lastLocation.longitude,
            )
        }
        reverseGeocode(lastLocation.latitude, lastLocation.longitude)

        viewModelScope.launch {
            combine(
                preferencesRepository.favorites,
                preferencesRepository.history,
                preferencesRepository.routes,
                preferencesRepository.settings,
            ) { favs, hist, rts, stg ->
                _uiState.update {
                    it.copy(
                        favorites = favs,
                        history = hist,
                        routes = rts,
                        settings = stg,
                        speedKmh = if (it.isMocking) it.speedKmh else stg.defaultSpeedKmh,
                        mapLayer = stg.mapLayer,
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            MockLocationService.isServiceRunning.collect { isRunning ->
                _uiState.update { it.copy(isMocking = isRunning) }
            }
        }

        viewModelScope.launch {
            MockLocationService.isPaused.collect { isPaused ->
                _uiState.update { it.copy(isPaused = isPaused) }
            }
        }

        viewModelScope.launch {
            MockLocationService.isRouteSimulating.collect { isSimulating ->
                _uiState.update { it.copy(isRouteSimulating = isSimulating) }
            }
        }

        viewModelScope.launch {
            combine(
                MockLocationService.currentLatitude,
                MockLocationService.currentLongitude,
                MockLocationService.currentBearing,
            ) { lat, lng, bearing ->
                _uiState.update {
                    it.copy(
                        currentLatitude = lat,
                        currentLongitude = lng,
                        bearing = bearing,
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            MockLocationService.currentWaypointIndex.collect { idx ->
                _uiState.update { it.copy(currentWaypointIndex = idx) }
            }
        }

        viewModelScope.launch {
            MockLocationService.hasSecurityError.collect { hasError ->
                _uiState.update { it.copy(hasMockPermissionError = hasError) }
            }
        }
    }

    fun startMocking() {
        val state = _uiState.value
        val allowed = mockLocationManager.checkMockLocationAllowed()
        if (!allowed) {
            _uiState.update { it.copy(hasMockPermissionError = true, isDeveloperOptionsGuideVisible = true) }
            return
        }

        MockLocationService.setCoordinates(
            lat = state.targetLatitude,
            lng = state.targetLongitude,
            alt = state.altitude,
            bearing = state.bearing,
        )
        MockLocationService.setSpeed(state.speedKmh)
        MockLocationService.setJitter(state.settings.jitterEnabled)
        MockLocationService.setUpdateInterval(state.settings.updateIntervalMs)

        MockLocationService.startService(
            context = context,
            lat = state.targetLatitude,
            lng = state.targetLongitude,
            alt = state.altitude,
            speedKmh = state.speedKmh,
            bearing = state.bearing,
        )

        preferencesRepository.saveLastLocation(state.targetLatitude, state.targetLongitude)
        _uiState.update { it.copy(centerMapTrigger = System.currentTimeMillis()) }
        preferencesRepository.addHistory(
            SavedLocation(
                id = UUID.randomUUID().toString(),
                name = state.locationName.ifBlank { "Custom Location" },
                latitude = state.targetLatitude,
                longitude = state.targetLongitude,
                altitude = state.altitude,
                tag = "History",
            ),
        )
    }

    fun stopMocking() {
        MockLocationService.stopService(context)
        _uiState.update { it.copy(isMocking = false, isPaused = false, isRouteSimulating = false) }
    }

    fun togglePause() {
        if (_uiState.value.isPaused) {
            MockLocationService.resumeService(context)
        } else {
            MockLocationService.pauseService(context)
        }
    }

    fun setTargetLocation(latitude: Double, longitude: Double, updateName: Boolean = true) {
        _uiState.update {
            it.copy(
                targetLatitude = latitude,
                targetLongitude = longitude,
                currentLatitude = if (!it.isMocking) latitude else it.currentLatitude,
                currentLongitude = if (!it.isMocking) longitude else it.currentLongitude,
            )
        }
        preferencesRepository.saveLastLocation(latitude, longitude)
        if (_uiState.value.isMocking && !_uiState.value.isRouteSimulating) {
            MockLocationService.setCoordinates(latitude, longitude, _uiState.value.altitude, _uiState.value.bearing)
        }
        if (updateName) {
            reverseGeocode(latitude, longitude)
        }
    }

    fun setSpeed(speedKmh: Float) {
        _uiState.update { it.copy(speedKmh = speedKmh) }
        MockLocationService.setSpeed(speedKmh)
    }

    fun setAltitude(altitude: Double) {
        _uiState.update { it.copy(altitude = altitude) }
    }

    fun setBearing(bearing: Float) {
        _uiState.update { it.copy(bearing = bearing) }
    }

    fun toggleJoystick() {
        _uiState.update { it.copy(isJoystickVisible = !it.isJoystickVisible) }
    }

    fun moveWithJoystick(angleDegrees: Double, distanceMeters: Double) {
        val currentLat = if (_uiState.value.isMocking) _uiState.value.currentLatitude else _uiState.value.targetLatitude
        val currentLng = if (_uiState.value.isMocking) _uiState.value.currentLongitude else _uiState.value.targetLongitude

        val (newLat, newLng) = MockLocationManager.calculateNewPosition(
            lat = currentLat,
            lng = currentLng,
            bearingDegrees = angleDegrees,
            distanceMeters = distanceMeters,
        )

        _uiState.update {
            it.copy(
                targetLatitude = newLat,
                targetLongitude = newLng,
                currentLatitude = newLat,
                currentLongitude = newLng,
                bearing = angleDegrees.toFloat(),
            )
        }

        if (_uiState.value.isMocking) {
            MockLocationService.setCoordinates(newLat, newLng, _uiState.value.altitude, angleDegrees.toFloat())
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(350)
                performPlaceSearch(query)
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun selectSearchResult(result: SearchResultPlace) {
        setTargetLocation(result.latitude, result.longitude, updateName = false)
        _uiState.update {
            it.copy(
                locationName = result.displayName,
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                centerMapTrigger = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun performPlaceSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        val results = withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=6&addressdetails=1")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Geto-GPSMock-AndroidApp")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    val list = mutableListOf<SearchResultPlace>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.optString("display_name", "")
                        val lat = obj.optDouble("lat", 0.0)
                        val lon = obj.optDouble("lon", 0.0)
                        val type = obj.optString("type", "")
                        if (lat != 0.0 && lon != 0.0) {
                            list.add(SearchResultPlace(name, lat, lon, type))
                        }
                    }
                    list
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}")
                emptyList()
            }
        }
        _uiState.update { it.copy(searchResults = results, isSearching = false) }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double) {
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(200)
                val url = URL("https://nominatim.openstreetmap.org/reverse?lat=$latitude&lon=$longitude&format=json")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Geto-GPSMock-AndroidApp")
                connection.connectTimeout = 4000
                connection.readTimeout = 4000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val displayName = jsonObject.optString("display_name", "")
                    if (displayName.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(locationName = displayName) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Reverse geocoding error: ${e.message}")
            }
        }
    }

    fun saveFavorite(name: String, tag: String) {
        val location = SavedLocation(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { _uiState.value.locationName },
            latitude = _uiState.value.targetLatitude,
            longitude = _uiState.value.targetLongitude,
            altitude = _uiState.value.altitude,
            tag = tag.ifBlank { "Favorite" },
        )
        preferencesRepository.addFavorite(location)
        _uiState.update { it.copy(showSaveFavoriteDialog = false) }
    }

    fun deleteFavorite(id: String) {
        preferencesRepository.deleteFavorite(id)
    }

    fun teleportToLocation(savedLocation: SavedLocation) {
        setTargetLocation(savedLocation.latitude, savedLocation.longitude, updateName = false)
        _uiState.update {
            it.copy(
                locationName = savedLocation.name,
                altitude = savedLocation.altitude,
                showFavoritesBottomSheet = false,
                centerMapTrigger = System.currentTimeMillis(),
            )
        }
    }

    fun centerMapOnPin() {
        _uiState.update { it.copy(centerMapTrigger = System.currentTimeMillis()) }
    }

    fun startRouteSimulation(route: MockRoute) {
        _uiState.update {
            it.copy(
                activeRouteWaypoints = route.waypoints,
                showRoutePlannerDialog = false,
                isRouteSimulating = true,
                speedKmh = route.speedKmh,
            )
        }
        MockLocationService.setRoute(route.waypoints, route.loopMode)
        MockLocationService.startRoute(context, route.speedKmh, route.loopMode)
    }

    fun stopRouteSimulation() {
        MockLocationService.stopRoute(context)
        _uiState.update { it.copy(isRouteSimulating = false) }
    }

    fun saveCustomRoute(route: MockRoute) {
        preferencesRepository.saveRoute(route)
    }

    fun deleteCustomRoute(id: String) {
        preferencesRepository.deleteRoute(id)
    }

    fun updateSettings(settings: GpsMockSettings) {
        preferencesRepository.updateSettings(settings)
        MockLocationService.setJitter(settings.jitterEnabled)
        MockLocationService.setUpdateInterval(settings.updateIntervalMs)
        _uiState.update { it.copy(settings = settings, showSettingsDialog = false) }
    }

    fun setMapLayer(layer: String) {
        val updated = _uiState.value.settings.copy(mapLayer = layer)
        preferencesRepository.updateSettings(updated)
        _uiState.update { it.copy(mapLayer = layer) }
    }

    fun openDeveloperOptions() {
        mockLocationManager.openDeveloperOptions()
    }

    fun toggleHideUi() = _uiState.update { it.copy(isUiHidden = !it.isUiHidden) }
    fun setHideUi(hidden: Boolean) = _uiState.update { it.copy(isUiHidden = hidden) }
    fun toggleBottomCardExpanded() = _uiState.update { it.copy(isBottomCardExpanded = !it.isBottomCardExpanded) }

    fun showManualCoordinatesDialog(show: Boolean) = _uiState.update { it.copy(showManualCoordinateDialog = show) }
    fun showSaveFavoriteDialog(show: Boolean) = _uiState.update { it.copy(showSaveFavoriteDialog = show) }
    fun showFavoritesBottomSheet(show: Boolean) = _uiState.update { it.copy(showFavoritesBottomSheet = show) }
    fun showRoutePlannerDialog(show: Boolean) = _uiState.update { it.copy(showRoutePlannerDialog = show) }
    fun showSettingsDialog(show: Boolean) = _uiState.update { it.copy(showSettingsDialog = show) }
    fun showDeveloperOptionsGuide(show: Boolean) = _uiState.update { it.copy(isDeveloperOptionsGuideVisible = show) }
    fun dismissMockPermissionError() = _uiState.update { it.copy(hasMockPermissionError = false) }
}
