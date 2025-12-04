package org.mz.mzdkplayer.ui.videoplayer.components


import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.tool.focusOnInitialVisibility
import org.mz.mzdkplayer.ui.screen.common.MyIconButton
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor


@OptIn(UnstableApi::class)
@Composable
fun SubtitleTrackPanel(
    selectedIndex: Int,
    onSelectedIndexChange: (currentIndex: Int) -> Unit,
    lists: MutableList<Tracks.Group>,
    exoPlayer: ExoPlayer,
    mediaUri: String
) {

    val focusRequester = remember { FocusRequester() }
    // 每次进入获取焦点
    val isVis = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
    ) {
        // --- 标题栏和按钮 ---
        Row(
            modifier = Modifier
                .width(360.dp)
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题
            Text(
                text = "字幕轨道",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.weight(1f)
            )

            // 按钮：手动加载外部字幕
            MyIconButton(
                text = "加载外部字幕",
                icon = R.drawable.baseline_search_24, // 请替换为你真实的图标资源 ID
                onClick = {
                    val externalSubtitles = findExternalSubtitles(mediaUri)
                    val finalUri = finalUri(mediaUri)
                    val mediaItem = MediaItem.Builder().setUri(finalUri).setSubtitleConfigurations(
                        externalSubtitles
                    ).build()
                    exoPlayer.setMediaItem(mediaItem, false)
                    exoPlayer.prepare()

                    Log.d(
                        "SubtitleLoader",
                        "Deferred adding of ${externalSubtitles.size} external subtitles via updateMediaItem."
                    )
                } // 绑定到外部传进来的加载函数
            )
        }
        LazyColumn(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .focusRequester(focusRequester), state = listState
        ) {
            if (lists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .width(360.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该文件无字幕轨道",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                }
            } else {
                //LazyColumn滚到到当前选择位置
                coroutineScope.launch {
                    listState.animateScrollToItem(index = selectedIndex)
                }
                items(lists.size) { index ->
                    val trackFormat = lists[index].getTrackFormat(0)
                    // 1. 获取轨道信息
                    val trackLabel = trackFormat.label
                    val trackLang = trackFormat.language
                    val trackMimeType = trackFormat.codecs ?: "未知格式"

                    // 2. 识别是否为外部加载字幕
                    val isExternalAutoSearch = trackLabel?.startsWith("[外部加载]") == true

                    // 3. 构造显示文本和颜色
                    val labelText = if (isExternalAutoSearch) {
                        // 移除内部标记，只显示文件名
                        trackLabel.replace("[外部加载] ", "")
                    } else if (!trackLabel.isNullOrEmpty()) {
                        trackLabel
                    } else {
                        ""
                    }

                    val languageText = Tools.getFullLanguageName(trackLang)

                    val titleText = if (isExternalAutoSearch) {
                        // 如果是外部搜索的，添加提示信息
                        "$languageText $labelText (未找到文件可能缓冲)"
                    } else if (!trackLabel.isNullOrEmpty()) {
                        // 如果有 Label (可能是内嵌的标题)
                        "$languageText $labelText"
                    } else {
                        // 只有 Language
                        "$languageText 字幕"
                    }

                    val titleColor =
                        if (isExternalAutoSearch) Color(0xFFFFCC00)  else Color.Transparent // 外部加载的用黄色高亮

                    ListItem(
                        modifier = if (index == selectedIndex /*选中的获取焦点*/) {
                            Modifier
                                .padding(
                                    start = 15.dp,
                                    end = 15.dp,
                                    top = 10.dp,
                                    bottom = 10.dp

                                )
                                .focusOnInitialVisibility(isVis)
                        } else Modifier.padding(
                            start = 15.dp,
                            end = 15.dp,
                            top = 10.dp,
                            bottom = 10.dp
                        ),
                        selected = false,
                        colors = myListItemCoverColor(),
                        headlineContent = {
                            if (isExternalAutoSearch) {
                                Text(
                                    text = titleText,
                                    color = Color(0xFFFFCC00)  // 使用计算出的颜色
                                )
                            }else{
                                Text(
                                    text = titleText,
                                )
                            }
                        },
                        overlineContent = {
                            Text(
                                "字幕格式 · $trackMimeType"
                            )
                        },

                        leadingContent = if (selectedIndex == index) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Localized description",
                                )
                            }
                        } else null,
//                trailingContent = {
//
//                        Icon(
//                            painterResource(id = Tools.audioFormatIconType(lists[index].getTrackFormat(0))),
//                            contentDescription = "Localized description",
//                        )
//
//
//                },
                        onClick = {
                            onSelectedIndexChange(index)
                            exoPlayer.trackSelectionParameters =
                                exoPlayer.trackSelectionParameters.buildUpon().setOverrideForType(
                                    TrackSelectionOverride(
                                        lists[index].mediaTrackGroup,
                                        0
                                    )
                                ).build();
                        }
                    )
                }


            }
        }
    }
}