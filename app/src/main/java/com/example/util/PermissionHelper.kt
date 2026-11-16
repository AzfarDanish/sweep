package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.PermissionAccessState

object PermissionHelper {

    fun requiredMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun evaluateAccessState(context: Context): PermissionAccessState {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val hasFullImages = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val hasFullVideos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                val hasPartial = isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    hasFullImages || hasFullVideos -> PermissionAccessState.FULL
                    hasPartial -> PermissionAccessState.PARTIAL
                    else -> PermissionAccessState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasImages = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                if (hasImages || hasVideos) PermissionAccessState.FULL else PermissionAccessState.DENIED
            }
            else -> {
                if (isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    PermissionAccessState.FULL
                } else {
                    PermissionAccessState.DENIED
                }
            }
        }
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
