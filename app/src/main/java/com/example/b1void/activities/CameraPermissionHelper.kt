package com.example.b1void.activities

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraPermissionHelper(
    private val activity: Activity,
    private val onPermissionsGranted: () -> Unit
) {

    fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions() {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                onPermissionsGranted()
            } else {
                // Handle the case where the user denies the permissions.
                // For now, we can just log it or show a toast.
                // The original activity showed a toast, but for this helper,
                // we might want to delegate that back via another callback.
                // For simplicity, we'll just log it.
                android.util.Log.e(TAG, "Permissions not granted by the user.")
                // Optionally, we could add an onPermissionsDenied callback.
            }
        }
    }

    companion object {
        private const val TAG = "CameraPermissionHelper"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }
}
