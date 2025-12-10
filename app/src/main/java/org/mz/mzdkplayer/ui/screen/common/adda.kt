package org.mz.mzdkplayer.ui.screen.common

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.mz.mzdkplayer.R

@Preview
@Composable
fun MyDialogFinalFix() {
    val openCustomDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        Button(
            onClick = {},
            onLongClick = {
                openCustomDialog.value = true
            }
        ) {
            Text(text = "长按显示弹窗", style = MaterialTheme.typography.headlineSmall)
        }

        if (openCustomDialog.value) {
            Dialog(
                onDismissRequest = { openCustomDialog.value = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                // 记录是否允许点击
                var allowClick by remember { mutableStateOf(false) }
                Log.d("allowClick",allowClick.toString())

                // 捕获按键事件
                Box(
                    modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                        // 如果是 OK 键（DPAD_CENTER 或 ENTER）
                        if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)&&!allowClick) {
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
                            text = "修改",
                            icon = R.drawable.save24dp,
                            onClick = {
                                if (allowClick) {
                                    Toast.makeText(context, "点击修改", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        MyIconButton(
                            text = "删除",
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
    }
}
// =======================================================
// 1. 核心状态逻辑
// =======================================================

@Composable
fun trackOkPressState(): Boolean {
    // 维护 OK 键是否被按下的状态
    val isOkPressed = remember { mutableStateOf(false) }

    // 使用 LaunchedEffect 确保状态在 Composable 生命周期内有效
    LaunchedEffect(Unit) {
        // 在实际应用中，你可能需要将这个状态管理提升到 ViewModel 或其他作用域
    }

    // 返回状态，让其他 Composable 可以读取
    return isOkPressed.value
}

@Composable
fun OkKeyDetectionScreen() {
    // 获取 OK 键的状态
    val isOkPressedState = remember { mutableStateOf(false) }
    val isOkPressed by isOkPressedState // 使用 by 关键字读取当前值

    Column(
        // =======================================================
        // 2. 监听按键事件
        // =======================================================
        modifier = Modifier
            .onPreviewKeyEvent {
                // 检查按键是否是 D-Pad 的中键 (KEYCODE_DPAD_CENTER) 或 Enter 键 (KEYCODE_ENTER)
                if (it.key == Key.DirectionCenter || it.key == Key.Enter) {
                    when (it.type) {
                        KeyEventType.KeyDown -> {
                            // 按下事件：设置状态为 true
                            isOkPressedState.value = true
                            // 返回 false 表示事件未被完全消耗，允许它继续传递给子组件
                            return@onPreviewKeyEvent false
                        }
                        KeyEventType.KeyUp -> {
                            // 松开事件：设置状态为 false
                            isOkPressedState.value = false
                            // 返回 false
                            return@onPreviewKeyEvent false
                        }
                    }
                }
                // 对于其他按键，不处理
                false
            }
            .padding(32.dp)
    ) {
        Text(text = "按下状态检测演示:")

        // =======================================================
        // 3. 根据状态显示信息
        // =======================================================
        if (isOkPressed) {
            Text(text = "🟢 OK 键被按着！", color = androidx.compose.ui.graphics.Color.Green)
        } else {
            Text(text = "⚪ OK 键已松开。", color = androidx.compose.ui.graphics.Color.Gray)
        }
    }
}

@Preview
@Composable
fun PreviewOkKeyDetection() {
    OkKeyDetectionScreen()
}