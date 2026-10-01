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
package com.android.geto.feature.gpsmock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.geto.feature.gpsmock.manager.MockLocationManager
import com.android.geto.feature.gpsmock.model.MockCoordinate
import com.android.geto.feature.gpsmock.model.RouteLoopMode
import com.android.geto.framework.notificationmanager.AndroidNotificationManagerWrapper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

private const val NOTIFICATION_ID = 2024
private const val CHANNEL_ID = AndroidNotificationManagerWrapper.NOTIFICATION_CHANNEL_ID

@AndroidEntryPoint
class MockLocationService : Service() {

    @Inject
    lateinit var mockLocationManager: MockLocationManager

    @Inject
    lateinit var androidNotificationManagerWrapper: AndroidNotificationManagerWrapper

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var mockLoopJob: Job? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Geto:MockLocationWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MOCK -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, _currentLatitude.value)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, _currentLongitude.value)
                val alt = intent.getDoubleExtra(EXTRA_ALTITUDE, _currentAltitude.value)
                val speed = intent.getFloatExtra(EXTRA_SPEED_KMH, _currentSpeedKmh.value)
                val bearing = intent.getFloatExtra(EXTRA_BEARING, _currentBearing.value)

                _currentLatitude.value = lat
                _currentLongitude.value = lng
                _currentAltitude.value = alt
                _currentSpeedKmh.value = speed
                _currentBearing.value = bearing
                _isPaused.value = false
                _isRouteSimulating.value = false

                startForegroundServiceCompat()
                startMockLoop()
            }
            ACTION_STOP_MOCK -> {
                stopMocking()
            }
            ACTION_PAUSE_MOCK -> {
                _isPaused.value = true
                updateNotification()
            }
            ACTION_RESUME_MOCK -> {
                _isPaused.value = false
                updateNotification()
            }
            ACTION_UPDATE_LOCATION -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, _currentLatitude.value)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, _currentLongitude.value)
                val bearing = intent.getFloatExtra(EXTRA_BEARING, _currentBearing.value)
                _currentLatitude.value = lat
                _currentLongitude.value = lng
                _currentBearing.value = bearing
                updateNotification()
            }
            ACTION_SET_SPEED -> {
                _currentSpeedKmh.value = intent.getFloatExtra(EXTRA_SPEED_KMH, 10f)
            }
            ACTION_START_ROUTE -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED_KMH, 15f)
                val loopMode = intent.getStringExtra(EXTRA_LOOP_MODE) ?: RouteLoopMode.REPEAT.name
                _currentSpeedKmh.value = speed
                _routeLoopMode.value = RouteLoopMode.valueOf(loopMode)
                _isRouteSimulating.value = true
                _currentWaypointIndex.value = 0
                _isPaused.value = false

                startForegroundServiceCompat()
                startMockLoop()
            }
            ACTION_STOP_ROUTE -> {
                _isRouteSimulating.value = false
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundType,
        )
    }

    private fun startMockLoop() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours wake lock for continuous background location spoofing
        }
        mockLoopJob?.cancel()
        mockLoopJob = serviceScope.launch {
            while (isActive) {
                if (!_isPaused.value) {
                    if (_isRouteSimulating.value && _routeWaypoints.value.isNotEmpty()) {
                        stepRouteSimulation()
                    } else {
                        stepStaticLocation()
                    }
                }
                delay(_updateIntervalMs.value)
            }
        }
    }

    private fun stepStaticLocation() {
        var lat = _currentLatitude.value
        var lng = _currentLongitude.value

        if (_jitterEnabled.value) {
            val jitterLat = (Random.nextDouble() - 0.5) * 0.00003
            val jitterLng = (Random.nextDouble() - 0.5) * 0.00003
            lat += jitterLat
            lng += jitterLng
        }

        val success = mockLocationManager.pushLocation(
            latitude = lat,
            longitude = lng,
            altitude = _currentAltitude.value,
            accuracy = _currentAccuracy.value,
            speedMps = (_currentSpeedKmh.value / 3.6f),
            bearing = _currentBearing.value,
        )

        _hasSecurityError.value = !success
    }

    private fun stepRouteSimulation() {
        val waypoints = _routeWaypoints.value
        if (waypoints.size < 2) return

        val currentIndex = _currentWaypointIndex.value
        val nextIndex = (currentIndex + 1) % waypoints.size

        val currentWp = waypoints[currentIndex]
        val targetWp = waypoints[nextIndex]

        val totalDist = MockLocationManager.calculateDistance(
            currentWp.latitude, currentWp.longitude,
            targetWp.latitude, targetWp.longitude,
        )

        val speedMps = _currentSpeedKmh.value / 3.6
        val stepMeters = speedMps * (_updateIntervalMs.value / 1000.0)

        val distToTarget = MockLocationManager.calculateDistance(
            _currentLatitude.value, _currentLongitude.value,
            targetWp.latitude, targetWp.longitude,
        )

        if (distToTarget <= stepMeters || distToTarget < 2.0) {
            // Reached waypoint
            _currentLatitude.value = targetWp.latitude
            _currentLongitude.value = targetWp.longitude

            if (nextIndex == 0 && _routeLoopMode.value == RouteLoopMode.ONCE) {
                _isRouteSimulating.value = false
            } else {
                _currentWaypointIndex.value = nextIndex
            }
        } else {
            val bearing = MockLocationManager.calculateBearing(
                _currentLatitude.value, _currentLongitude.value,
                targetWp.latitude, targetWp.longitude,
            )
            _currentBearing.value = bearing

            val newPos = MockLocationManager.calculateNewPosition(
                _currentLatitude.value, _currentLongitude.value,
                bearing.toDouble(), stepMeters,
            )
            _currentLatitude.value = newPos.first
            _currentLongitude.value = newPos.second
        }

        mockLocationManager.pushLocation(
            latitude = _currentLatitude.value,
            longitude = _currentLongitude.value,
            altitude = _currentAltitude.value,
            accuracy = _currentAccuracy.value,
            speedMps = (_currentSpeedKmh.value / 3.6f),
            bearing = _currentBearing.value,
        )
    }

    private fun updateNotification() {
        if (_isServiceRunning.value) {
            androidNotificationManagerWrapper.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val latStr = String.format(Locale.US, "%.5f", _currentLatitude.value)
        val lngStr = String.format(Locale.US, "%.5f", _currentLongitude.value)
        val status = if (_isPaused.value) "Paused" else if (_isRouteSimulating.value) "Simulating Route (${_currentSpeedKmh.value.toInt()} km/h)" else "Active Spoofing"

        val stopIntent = Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP_MOCK }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val pauseResumeIntent = Intent(this, MockLocationService::class.java).apply {
            action = if (_isPaused.value) ACTION_RESUME_MOCK else ACTION_PAUSE_MOCK
        }
        val pauseResumePendingIntent = PendingIntent.getService(this, 2, pauseResumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GPS Mock: $status")
            .setContentText("Lat: $latStr, Lng: $lngStr")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (_isPaused.value) "Resume" else "Pause",
                pauseResumePendingIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent,
            )

        return builder.build()
    }

    private fun stopMocking() {
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
        mockLoopJob?.cancel()
        mockLoopJob = null
        _isServiceRunning.value = false
        _isPaused.value = false
        _isRouteSimulating.value = false
        mockLocationManager.cleanupTestProviders()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMocking()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_MOCK = "com.android.geto.action.START_MOCK"
        const val ACTION_STOP_MOCK = "com.android.geto.action.STOP_MOCK"
        const val ACTION_PAUSE_MOCK = "com.android.geto.action.PAUSE_MOCK"
        const val ACTION_RESUME_MOCK = "com.android.geto.action.RESUME_MOCK"
        const val ACTION_UPDATE_LOCATION = "com.android.geto.action.UPDATE_LOCATION"
        const val ACTION_SET_SPEED = "com.android.geto.action.SET_SPEED"
        const val ACTION_START_ROUTE = "com.android.geto.action.START_ROUTE"
        const val ACTION_STOP_ROUTE = "com.android.geto.action.STOP_ROUTE"

        const val EXTRA_LATITUDE = "extra_lat"
        const val EXTRA_LONGITUDE = "extra_lng"
        const val EXTRA_ALTITUDE = "extra_alt"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"
        const val EXTRA_BEARING = "extra_bearing"
        const val EXTRA_LOOP_MODE = "extra_loop_mode"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused = _isPaused.asStateFlow()

        private val _isRouteSimulating = MutableStateFlow(false)
        val isRouteSimulating = _isRouteSimulating.asStateFlow()

        private val _currentLatitude = MutableStateFlow(37.7749)
        val currentLatitude = _currentLatitude.asStateFlow()

        private val _currentLongitude = MutableStateFlow(-122.4194)
        val currentLongitude = _currentLongitude.asStateFlow()

        private val _currentAltitude = MutableStateFlow(15.0)
        val currentAltitude = _currentAltitude.asStateFlow()

        private val _currentAccuracy = MutableStateFlow(2.5f)
        val currentAccuracy = _currentAccuracy.asStateFlow()

        private val _currentSpeedKmh = MutableStateFlow(10.0f)
        val currentSpeedKmh = _currentSpeedKmh.asStateFlow()

        private val _currentBearing = MutableStateFlow(0.0f)
        val currentBearing = _currentBearing.asStateFlow()

        private val _jitterEnabled = MutableStateFlow(false)
        val jitterEnabled = _jitterEnabled.asStateFlow()

        private val _updateIntervalMs = MutableStateFlow(1000L)
        val updateIntervalMs = _updateIntervalMs.asStateFlow()

        private val _hasSecurityError = MutableStateFlow(false)
        val hasSecurityError = _hasSecurityError.asStateFlow()

        private val _routeWaypoints = MutableStateFlow<List<MockCoordinate>>(emptyList())
        val routeWaypoints = _routeWaypoints.asStateFlow()

        private val _currentWaypointIndex = MutableStateFlow(0)
        val currentWaypointIndex = _currentWaypointIndex.asStateFlow()

        private val _routeLoopMode = MutableStateFlow(RouteLoopMode.REPEAT)
        val routeLoopMode = _routeLoopMode.asStateFlow()

        fun setCoordinates(lat: Double, lng: Double, alt: Double = 15.0, bearing: Float = 0f) {
            _currentLatitude.value = lat
            _currentLongitude.value = lng
            _currentAltitude.value = alt
            _currentBearing.value = bearing
        }

        fun setSpeed(speedKmh: Float) {
            _currentSpeedKmh.value = speedKmh
        }

        fun setJitter(enabled: Boolean) {
            _jitterEnabled.value = enabled
        }

        fun setUpdateInterval(intervalMs: Long) {
            _updateIntervalMs.value = intervalMs
        }

        fun setRoute(waypoints: List<MockCoordinate>, loopMode: RouteLoopMode = RouteLoopMode.REPEAT) {
            _routeWaypoints.value = waypoints
            _routeLoopMode.value = loopMode
            _currentWaypointIndex.value = 0
        }

        fun startService(
            context: Context,
            lat: Double,
            lng: Double,
            alt: Double = 15.0,
            speedKmh: Float = 10f,
            bearing: Float = 0f,
        ) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START_MOCK
                putExtra(EXTRA_LATITUDE, lat)
                putExtra(EXTRA_LONGITUDE, lng)
                putExtra(EXTRA_ALTITUDE, alt)
                putExtra(EXTRA_SPEED_KMH, speedKmh)
                putExtra(EXTRA_BEARING, bearing)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP_MOCK
            }
            context.startService(intent)
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_PAUSE_MOCK
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_RESUME_MOCK
            }
            context.startService(intent)
        }

        fun startRoute(context: Context, speedKmh: Float, loopMode: RouteLoopMode) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START_ROUTE
                putExtra(EXTRA_SPEED_KMH, speedKmh)
                putExtra(EXTRA_LOOP_MODE, loopMode.name)
            }
            context.startService(intent)
        }

        fun stopRoute(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP_ROUTE
            }
            context.startService(intent)
        }
    }
}
