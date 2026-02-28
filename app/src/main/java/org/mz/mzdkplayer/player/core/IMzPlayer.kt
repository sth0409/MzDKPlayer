package org.mz.mzdkplayer.player.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import org.mz.mzdkplayer.ui.screen.vm.VideoPlayerStatus

interface IMzPlayer {
    val isPlaying: Boolean
    val isPlayingFlow: StateFlow<Boolean>
    val currentPosition: Long
    val duration: Long
    val playerStatus: StateFlow<VideoPlayerStatus>
    // 轨道状态流，UI直接监听这些流来刷新面板
    val videoTracks: StateFlow<List<MzVideoTrack>>
    val audioTracks: StateFlow<List<MzBasicTrack>>
    val subtitleTracks: StateFlow<List<MzBasicTrack>>
    /**
     * 视频源的原始宽度（像素），用于 PGS/位图字幕正确定位
     */
    val videoWidth: Int

    /**
     * 视频源的原始高度（像素）
     */
    val videoHeight: Int
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekForward(ms: Long = 30000)
    fun seekBack(ms: Long = 30000)

    // 轨道切换接口
    fun selectVideoTrack(track: MzVideoTrack)
    fun selectAudioTrack(track: MzBasicTrack)
    fun selectSubtitleTrack(track: MzBasicTrack)
    // 统一的错误回调
    var onError: ((String) -> Unit)?
    // 统一的字幕/排版信息回调 (对应原 onCues)
    var onCuesChanged: ((Any) -> Unit)?
    fun release()

    // 核心：把渲染视图交给实现类去做，Compose里直接调用
    @Composable
    fun PlayerView(modifier: Modifier)
}