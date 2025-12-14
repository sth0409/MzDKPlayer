package org.mz.mzdkplayer.ui.screen.common

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mz.mzdkplayer.R

@Composable
fun MyFileDialog(onDismissRequest: () -> Unit) {
    val openCustomDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { onDismissRequest },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    )
    {
        // 记录是否允许点击
        var allowClick by remember { mutableStateOf(false) }
        Log.d("allowClick", allowClick.toString())

        // 捕获按键事件
        Box(
            modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                // 如果是 OK 键（DPAD_CENTER 或 ENTER）
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) && !allowClick) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            // 只要 OK 还在按住，就不允许点击
                            allowClick = false
                            true    // 吃掉事件
                        }

                        KeyEventType.KeyUp -> {
                            // 松开 OK 键后才允许点击
                            allowClick = true
                            true
                        }

                        else -> true
                    }
                } else {
                    false
                }
            }
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                MyIconButton(
                    text = "修改文件对应影视信息",
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.save24dp,
                    onClick = {
                        if (allowClick) {
                            Toast.makeText(context, "点击修改", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                MyIconButton(
                    text = "返回",
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.chevronright24dp,
                    onClick = {
                        if (allowClick) {
                            Toast.makeText(context, "点击删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}
