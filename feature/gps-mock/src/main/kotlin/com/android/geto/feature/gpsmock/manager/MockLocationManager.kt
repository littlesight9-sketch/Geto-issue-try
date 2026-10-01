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
package com.android.geto.feature.gpsmock.manager

import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "MockLocationManager"

@Singleton
class MockLocationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )

    private var providersAdded = false

    fun initializeTestProviders(): Boolean {
        return try {
            for (provider in providers) {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val properties = ProviderProperties.Builder()
                        .setHasNetworkRequirement(false)
                        .setHasSatelliteRequirement(true)
                        .setHasCellRequirement(false)
                        .setHasMonetaryCost(false)
                        .setHasAltitudeSupport(true)
                        .setHasSpeedSupport(true)
                        .setHasBearingSupport(true)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .build()
                    locationManager.addTestProvider(provider, properties)
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE,
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
            }
            providersAdded = true
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Mock location permission not granted: ${e.message}")
            providersAdded = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error adding test providers: ${e.message}", e)
            providersAdded = false
            false
        }
    }

    fun pushLocation(
        latitude: Double,
        longitude: Double,
        altitude: Double = 10.0,
        accuracy: Float = 2.0f,
        speedMps: Float = 0.0f,
        bearing: Float = 0.0f,
    ): Boolean {
        if (!providersAdded) {
            val initialized = initializeTestProviders()
            if (!initialized) return false
        }

        val currentTime = System.currentTimeMillis()
        val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

        return try {
            for (provider in providers) {
                val mockLocation = Location(provider).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    this.altitude = altitude
                    this.accuracy = accuracy
                    this.speed = speedMps
                    this.bearing = bearing
                    this.time = currentTime
                    this.elapsedRealtimeNanos = elapsedRealtimeNanos
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        this.verticalAccuracyMeters = 1.0f
                        this.speedAccuracyMetersPerSecond = 0.5f
                        this.bearingAccuracyDegrees = 1.0f
                    }
                }
                locationManager.setTestProviderLocation(provider, mockLocation)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during setTestProviderLocation: ${e.message}")
            providersAdded = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing mock location: ${e.message}")
            false
        }
    }

    fun cleanupTestProviders() {
        try {
            for (provider in providers) {
                try {
                    locationManager.setTestProviderEnabled(provider, false)
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing test providers: ${e.message}")
        } finally {
            providersAdded = false
        }
    }

    fun checkMockLocationAllowed(): Boolean {
        return try {
            val testProvider = "gps_mock_test_probe"
            try {
                locationManager.removeTestProvider(testProvider)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val properties = ProviderProperties.Builder().build()
                locationManager.addTestProvider(testProvider, properties)
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    testProvider,
                    false, false, false, false, false, false, false,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
                )
            }
            locationManager.removeTestProvider(testProvider)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun openDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open settings: ${e.message}")
            }
        }
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

        fun calculateNewPosition(
            lat: Double,
            lng: Double,
            bearingDegrees: Double,
            distanceMeters: Double,
        ): Pair<Double, Double> {
            val distRatio = distanceMeters / EARTH_RADIUS_METERS
            val bearingRad = Math.toRadians(bearingDegrees)
            val latRad = Math.toRadians(lat)
            val lngRad = Math.toRadians(lng)

            val newLatRad = asin(
                sin(latRad) * cos(distRatio) + cos(latRad) * sin(distRatio) * cos(bearingRad),
            )
            val newLngRad = lngRad + atan2(
                sin(bearingRad) * sin(distRatio) * cos(latRad),
                cos(distRatio) - sin(latRad) * sin(newLatRad),
            )

            return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLngRad))
        }

        fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        fun calculateBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            val dLngRad = Math.toRadians(lng2 - lng1)

            val y = sin(dLngRad) * cos(lat2Rad)
            val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLngRad)
            val bearingRad = atan2(y, x)
            val bearingDeg = Math.toDegrees(bearingRad)
            return ((bearingDeg + 360) % 360).toFloat()
        }
    }
}
