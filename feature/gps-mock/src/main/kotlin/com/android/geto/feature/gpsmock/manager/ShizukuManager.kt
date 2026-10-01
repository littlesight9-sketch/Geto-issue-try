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
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader

private const val TAG = "ShizukuManager"
const val SHIZUKU_PERMISSION_REQUEST_CODE = 8088

object ShizukuManager {

    /**
     * Returns true if the Shizuku background service binder is running on the device.
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            Log.d(TAG, "Shizuku binder not available: ${e.message}")
            false
        }
    }

    /**
     * Returns true if our app has been granted Shizuku permission by the user.
     */
    fun hasShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking Shizuku permission: ${e.message}")
            false
        }
    }

    /**
     * Triggers Shizuku's native permission request prompt UI ("Allow Geto to connect to Shizuku").
     */
    fun requestShizukuPermission() {
        if (isShizukuAvailable()) {
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Log.d(TAG, "Showing Shizuku request rationale")
                }
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            } catch (e: Throwable) {
                Log.e(TAG, "Error requesting Shizuku permission: ${e.message}")
            }
        }
    }

    private fun runShizukuProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    /**
     * Executes automatic setup commands via Shizuku shell process:
     * - Grants WRITE_SECURE_SETTINGS
     * - Enables MOCK_LOCATION app ops
     * - Enables mock location system settings
     */
    fun executeAutoSetupCommands(context: Context): ShizukuExecutionResult {
        if (!isShizukuAvailable()) {
            return ShizukuExecutionResult.Error("Shizuku service is not running. Please start Shizuku first.")
        }

        if (!hasShizukuPermission()) {
            requestShizukuPermission()
            return ShizukuExecutionResult.PermissionRequested
        }

        val packageName = context.packageName
        val commands = listOf(
            "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
            "appops set $packageName MOCK_LOCATION allow",
            "settings put secure mock_location 1",
        )

        var lastError: String? = null
        var successCount = 0

        for (cmd in commands) {
            try {
                val process = runShizukuProcess(arrayOf("sh", "-c", cmd))
                val exitCode = process.waitFor()

                val errorOutput = process.errorStream.bufferedReader().use(BufferedReader::readText)
                if (exitCode == 0) {
                    successCount++
                } else {
                    lastError = errorOutput.ifBlank { "Exit code $exitCode for: $cmd" }
                    Log.e(TAG, "Shizuku command failed ($exitCode): $cmd -- Output: $errorOutput")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception running Shizuku command: $cmd", e)
                lastError = e.localizedMessage ?: "Unknown error"
            }
        }

        return if (successCount > 0) {
            ShizukuExecutionResult.Success("Successfully configured permissions and mock location settings via Shizuku!")
        } else {
            ShizukuExecutionResult.Error(lastError ?: "Failed to execute commands via Shizuku")
        }
    }
}

sealed class ShizukuExecutionResult {
    data class Success(val message: String) : ShizukuExecutionResult()
    data class Error(val errorMessage: String) : ShizukuExecutionResult()
    data object PermissionRequested : ShizukuExecutionResult()
}
