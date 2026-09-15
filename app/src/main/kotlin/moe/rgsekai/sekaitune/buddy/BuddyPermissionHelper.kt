/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object BuddyPermissionHelper {
    const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                NOTIFICATION_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                NOTIFICATION_PERMISSION
            )
        } else {
            false
        }
    }

    fun requestNotificationPermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(NOTIFICATION_PERMISSION),
                    requestCode
                )
            }
        }
    }
}
