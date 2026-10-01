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

import com.android.geto.feature.gpsmock.model.GpsMockSettings
import com.android.geto.feature.gpsmock.model.MockCoordinate
import com.android.geto.feature.gpsmock.model.MockRoute
import com.android.geto.feature.gpsmock.model.SavedLocation
import com.android.geto.feature.gpsmock.model.SearchResultPlace

data class GpsMockUiState(
    val isMocking: Boolean = false,
    val isPaused: Boolean = false,
    val isRouteSimulating: Boolean = false,
    val targetLatitude: Double = 37.7749,
    val targetLongitude: Double = -122.4194,
    val currentLatitude: Double = 37.7749,
    val currentLongitude: Double = -122.4194,
    val altitude: Double = 15.0,
    val accuracy: Float = 2.5f,
    val speedKmh: Float = 10.0f,
    val bearing: Float = 0.0f,
    val locationName: String = "San Francisco, CA",
    val searchQuery: String = "",
    val searchResults: List<SearchResultPlace> = emptyList(),
    val isSearching: Boolean = false,
    val favorites: List<SavedLocation> = emptyList(),
    val history: List<SavedLocation> = emptyList(),
    val routes: List<MockRoute> = emptyList(),
    val activeRouteWaypoints: List<MockCoordinate> = emptyList(),
    val currentWaypointIndex: Int = 0,
    val settings: GpsMockSettings = GpsMockSettings(),
    val isJoystickVisible: Boolean = false,
    val hasMockPermissionError: Boolean = false,
    val isDeveloperOptionsGuideVisible: Boolean = false,
    val showManualCoordinateDialog: Boolean = false,
    val showSaveFavoriteDialog: Boolean = false,
    val showFavoritesBottomSheet: Boolean = false,
    val showRoutePlannerDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val mapLayer: String = "streets",
    val mapFollowMode: Boolean = true,
    val isUiHidden: Boolean = false,
    val isBottomCardExpanded: Boolean = false,
    val centerMapTrigger: Long = 0L,
)
