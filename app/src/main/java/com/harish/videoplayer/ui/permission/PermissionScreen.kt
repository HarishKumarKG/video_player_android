package com.harish.videoplayer.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.harish.videoplayer.R
import com.harish.videoplayer.ui.navigation.AppNavHost
import com.harish.videoplayer.ui.theme.BrandBackground
import com.harish.videoplayer.ui.theme.VideoPlayerTheme

/** Permission needed to read videos: READ_MEDIA_VIDEO on Android 13+, READ_EXTERNAL_STORAGE below. */
private val videoPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/** Everything we ask for in one dialog. Notifications are optional (Android 13+ only). */
private val permissionsToRequest: Array<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(videoPermission, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(videoPermission)
    }

private fun hasVideoPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionGate() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasVideoPermission(context)) }
    var deniedOnce by remember { mutableStateOf(false) }

    // Coming back from the app's Settings page: check again, the user may have allowed it there
    LifecycleResumeEffect(Unit) {
        granted = hasVideoPermission(context)
        onPauseOrDispose { }
    }

    // Activity Result API: replaces requestPermissions() + onRequestPermissionsResult()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results[videoPermission] == true
        if (!granted) deniedOnce = true
    }

    if (granted) {
        AppNavHost()
    } else {
        BrandBackground(modifier = Modifier.fillMaxSize()) {
            PermissionRequestScreen(
                deniedOnce = deniedOnce,
                onGrantClick = { launcher.launch(permissionsToRequest) },
                onOpenSettingsClick = {
                    // After "Don't allow" twice Android stops showing the dialog,
                    // so the only way back is the app's settings page.
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    deniedOnce: Boolean,
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding() // keep content clear of status/navigation bars
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.baseline_video_library_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your videos, your way",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (deniedOnce) {
                "Permission is needed to list the videos on your device."
            } else {
                "Allow access to videos so Harish Video can show and play them."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Button(
            onClick = onGrantClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("Allow access")
        }
        if (deniedOnce) {
            TextButton(onClick = onOpenSettingsClick) {
                Text("Open app settings")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionRequestScreenPreview() {
    VideoPlayerTheme(darkTheme = false) {
        PermissionRequestScreen(deniedOnce = false, onGrantClick = {}, onOpenSettingsClick = {})
    }
}
