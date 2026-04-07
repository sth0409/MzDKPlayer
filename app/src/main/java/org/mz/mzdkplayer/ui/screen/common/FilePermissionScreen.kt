package org.mz.mzdkplayer.ui.screen.common
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.magnifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.core.net.toUri
import org.mz.mzdkplayer.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FilePermissionScreen() {
    val context = LocalContext.current

    // 对于 Android 10 及以下版本，请求读写权限
    val readPermissionState = rememberPermissionState(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    val writePermissionState = rememberPermissionState(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
            if (Environment.isExternalStorageManager()) {
                Text(stringResource(R.string.ui_label_all_file_access_granted), color = Color.White)
                // 这里可以放置你的应用内容
            } else {
                Text(stringResource(R.string.ui_label_all_file_access_needed),color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                MyIconButton(
                    text = stringResource(R.string.ui_label_request_storage_permission),
                    icon = R.drawable.foldermanaged24dp,
                    onClick = {
                    requestManageStoragePermission(context)
                })

            }
        } else {
            // Android 10 及以下版本处理
            if (readPermissionState.status.isGranted && writePermissionState.status.isGranted) {
                Text(stringResource(R.string.ui_label_storage_permission_granted), color = Color.White)
                // 这里可以放置你的应用内容
            } else {
                val textToShow = if (readPermissionState.status.shouldShowRationale ||
                    writePermissionState.status.shouldShowRationale
                ) {
                    context.getString(R.string.ui_label_app_needs_storage_permission)
                } else {
                    context.getString(R.string.ui_label_storage_permission_needed_for_files)
                }

                Text(textToShow, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                MyIconButton(
                    text = stringResource(R.string.ui_label_request_storage_permission),
                    icon = R.drawable.foldermanaged24dp,
                    onClick = {
                    readPermissionState.launchPermissionRequest()
                    writePermissionState.launchPermissionRequest()
                })
            }
        }
    }

    // 自动请求权限（可选）
    LaunchedEffect(Unit) {

            if (!readPermissionState.status.isGranted || !writePermissionState.status.isGranted) {
                readPermissionState.launchPermissionRequest()
                writePermissionState.launchPermissionRequest()
            }

    }
}
@RequiresApi(Build.VERSION_CODES.R)
private fun requestManageStoragePermission(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
    )

    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            continue
        }
    }
    Toast.makeText(context, context.getString(R.string.ui_label_manually_enable_permission_in_settings), Toast.LENGTH_LONG).show()
}