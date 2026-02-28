


package org.mz.mzdkplayer.tool

import android.util.DisplayMetrics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
@UnstableApi
fun SubtitleView(
    cueGroup: CueGroup?,
    modifier: Modifier = Modifier,
    videoSizeDp: IntSize, // 视频容器尺寸（dp）
    subtitleStyle: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 22.sp
    ),
    backgroundColor: Color = Color.Black.copy(alpha = 0.0f),
    sourceVideoWidth: Int,      // ← 新增
    sourceVideoHeight: Int,     // ← 新增
    forcePGSCenter: Boolean = false
) {
    if (cueGroup == null || cueGroup.cues.isEmpty()) {
        return
    }

    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()

    // 直接使用传入的参数
    val videoSourceWidth = sourceVideoWidth
    val videoSourceHeight = sourceVideoHeight

    // 计算视频在容器中的实际显示尺寸和位置
    val displayedVideoRect = if (videoSourceWidth > 0 && videoSourceHeight > 0) {
        calculateDisplayedVideoRect(
            containerWidthDp = videoSizeDp.width,
            containerHeightDp = videoSizeDp.height,
            sourceWidthPx = videoSourceWidth,
            sourceHeightPx = videoSourceHeight,
            density = density
        )
    } else {
        // 无法计算，假设视频填满容器
        VideoDisplayRect(videoSizeDp.width.toFloat(), videoSizeDp.height.toFloat(), 0f, 0f)
    }

    Log.d("SubtitleView", "Video container: ${videoSizeDp.width}x${videoSizeDp.height}cueGroup.cues.size ${cueGroup.cues.size}")
    Log.d("SubtitleView", "Video source: ${videoSourceWidth}x${videoSourceHeight}")
    Log.d("SubtitleView", "Displayed video: ${displayedVideoRect.widthDp}x${displayedVideoRect.heightDp} at (${displayedVideoRect.offsetXDp}, ${displayedVideoRect.offsetYDp})")


    Box(
        modifier = Modifier.fillMaxSize() // 主容器填满，内部元素使用传入的modifier定位
    ) {
        // 文本字幕 - 使用传入的modifier进行定位，如果未传入则默认底部居中
        Box(
            modifier = if (modifier == Modifier) {
                // 如果传入的是默认的空modifier，则使用底部居中
                Modifier.align(Alignment.BottomCenter)
            } else {
                // 否则使用传入的modifier
                modifier
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                cueGroup.cues.forEach { cue ->
                    // Render text cue
                    cue.text?.toString()?.takeIf { it.isNotEmpty() && it != "null" }?.let { text ->
                        // 创建带居中对齐的文本样式
                        val centeredTextStyle = subtitleStyle.copy(textAlign = TextAlign.Center)

                        // 测量文本尺寸
                        val textLayoutResult = textMeasurer.measure(
                            text = text,
                            style = centeredTextStyle
                        )

                        val textWidthDp = textLayoutResult.size.width / density
                        val textHeightDp = textLayoutResult.size.height / density

                        // 使用Canvas绘制文本
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(textWidthDp.dp)
                                    .height(textHeightDp.dp)
                            ) {
                                // 绘制背景
                                if (backgroundColor.alpha > 0) {
                                    drawRect(
                                        color = backgroundColor,
                                        size = size
                                    )
                                }

                                // 绘制文本 - 使用居中对齐的样式
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = text,
                                    style = centeredTextStyle,
                                    topLeft = androidx.compose.ui.geometry.Offset.Zero
                                )
                            }
                        }
                    }
                }
            }
        }
// === 位图字幕 ===
        // === 位图字幕 ===
        // === 位图字幕优化版 ===
        // 使用 Spacer + drawWithCache 代替多个 Box
        // 这样可以将 N 个 LayoutNode 减少为 1 个，极大降低 Compose 的树遍历开销
        if (cueGroup.cues.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        // 1. 预处理：将 Bitmap 转换为 ImageBitmap 并在缓存阶段准备好
                        // 这里我们过滤掉没有 bitmap 的 cue，只处理位图字幕
                        val validCues = cueGroup.cues.filter { it.bitmap != null }
                        val imageBitmaps = validCues.map { it.bitmap!!.asImageBitmap() }

                        onDrawWithContent {
                            // 遍历绘制，按照 index 顺序绘制（自然的 Z-Index）
                            // 如果需要严格遵循 cue.zIndex，可以在 validCues 处先排序
                            validCues.forEachIndexed { index, cue ->
                                val imageBitmap = imageBitmaps[index]
                                val bitmap = cue.bitmap!!

                                // === 下面是原本的数学计算逻辑，移植到 DrawScope 内部 ===
                                // 注意：在 drawScope 中，size.width/height 就是 Canvas 的像素大小
                                // 但我们需要基于 displayedVideoRect (dp) 来计算，所以需要转换

                                // 2. 锚点逻辑
                                val (x, y, positionAnchor, lineAnchor) = if (forcePGSCenter) {
                                    SubtitleAnchorInfo(0.5f, 0.90f, Cue.ANCHOR_TYPE_MIDDLE, Cue.ANCHOR_TYPE_START)
                                } else {
                                    val resolvedX = if (cue.position != Cue.DIMEN_UNSET) cue.position.coerceIn(0f, 1f) else 0.5f
                                    val resolvedY = if (cue.line != Cue.DIMEN_UNSET) cue.line.coerceIn(0f, 1f) else 0.5f
                                    SubtitleAnchorInfo(
                                        resolvedX, resolvedY,
                                        cue.positionAnchor, cue.lineAnchor
                                    )
                                }

                                val originalBitmapAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                                // 3. 尺寸计算 (Dp 转 Px，因为 DrawScope 使用 Px)
                                // displayedVideoRect 中的单位是 Dp，我们需要转回 Px 用于绘制计算，
                                // 或者直接用 displayedVideoRect * density 得到 Px

                                val videoRectWidthPx = displayedVideoRect.widthDp * density
                                val videoRectHeightPx = displayedVideoRect.heightDp * density
                                val videoOffsetX = displayedVideoRect.offsetXDp * density
                                val videoOffsetY = displayedVideoRect.offsetYDp * density

                                val (targetWidthPx, targetHeightPx) = run {
                                    val targetW = if (!forcePGSCenter && cue.size != Cue.DIMEN_UNSET) {
                                        videoRectWidthPx * cue.size
                                    } else {
                                        bitmap.width.toFloat()
                                    }

                                    val targetH = if (!forcePGSCenter && cue.bitmapHeight != Cue.DIMEN_UNSET) {
                                        videoRectHeightPx * cue.bitmapHeight
                                    } else {
                                        bitmap.height.toFloat()
                                    }

                                    when {
                                        !forcePGSCenter && cue.size != Cue.DIMEN_UNSET && cue.bitmapHeight != Cue.DIMEN_UNSET -> {
                                            val widthBasedHeight = targetW / originalBitmapAspectRatio
                                            val heightBasedWidth = targetH * originalBitmapAspectRatio
                                            if (widthBasedHeight <= targetH) targetW to widthBasedHeight else heightBasedWidth to targetH
                                        }
                                        !forcePGSCenter && cue.size != Cue.DIMEN_UNSET -> targetW to (targetW / originalBitmapAspectRatio)
                                        !forcePGSCenter && cue.bitmapHeight != Cue.DIMEN_UNSET -> (targetH * originalBitmapAspectRatio) to targetH
                                        else -> bitmap.width.toFloat() to bitmap.height.toFloat()
                                    }
                                }

                                // 4. 偏移量计算 (Px)
                                val contentOffsetX = when (positionAnchor) {
                                    Cue.ANCHOR_TYPE_START -> videoRectWidthPx * x
                                    Cue.ANCHOR_TYPE_MIDDLE -> videoRectWidthPx * x - targetWidthPx / 2
                                    Cue.ANCHOR_TYPE_END -> videoRectWidthPx * x - targetWidthPx
                                    else -> videoRectWidthPx * x
                                }

                                val contentOffsetY = when (lineAnchor) {
                                    Cue.ANCHOR_TYPE_START -> videoRectHeightPx * y
                                    Cue.ANCHOR_TYPE_MIDDLE -> videoRectHeightPx * y - targetHeightPx / 2
                                    Cue.ANCHOR_TYPE_END -> videoRectHeightPx * y - targetHeightPx
                                    else -> videoRectHeightPx * y
                                }

                                val finalX = videoOffsetX + contentOffsetX
                                val finalY = videoOffsetY + contentOffsetY

                                // 5. 绘制
                                drawImage(
                                    image = imageBitmap,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(bitmap.width, bitmap.height),
                                    dstOffset = IntOffset(finalX.toInt(), finalY.toInt()),
                                    dstSize = IntSize(targetWidthPx.toInt(), targetHeightPx.toInt()),
                                    // 关键优化：使用 Low (Bilinear) 可以在缩放时保持较好的平滑度且性能开销可控
                                    // 如果 PGS 锯齿严重，可以尝试 High
                                    filterQuality = FilterQuality.Low
                                )
                            }
                        }
                    }
            )
        }
    }
}

/**
 * 数据类表示视频显示区域
 */
data class VideoDisplayRect(
    val widthDp: Float,
    val heightDp: Float,
    val offsetXDp: Float,
    val offsetYDp: Float
)

/**
 * 计算视频在容器中按 ContentScale.Fit（保持宽高比，居中）显示时的实际尺寸和偏移。
 *
 * @return VideoDisplayRect(显示宽度 dp, 显示高度 dp, X偏移 dp, Y偏移 dp)
 */
private fun calculateDisplayedVideoRect(
    containerWidthDp: Int,
    containerHeightDp: Int,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    density: Float
): VideoDisplayRect {
    val containerWidthPx = containerWidthDp * density
    val containerHeightPx = containerHeightDp * density

    val sourceAspectRatio = sourceWidthPx.toFloat() / sourceHeightPx.toFloat()
    val containerAspectRatio = containerWidthPx / containerHeightPx

    val (displayedWidthPx, displayedHeightPx) = if (sourceAspectRatio > containerAspectRatio) {
        // 视频更宽，限制宽度，高度按比例缩放
        val w = containerWidthPx
        val h = w / sourceAspectRatio
        w to h
    } else {
        // 视频更高，限制高度，宽度按比例缩放
        val h = containerHeightPx
        val w = h * sourceAspectRatio
        w to h
    }

    // 居中对齐的偏移
    val offsetX = (containerWidthPx - displayedWidthPx) / 2
    val offsetY = (containerHeightPx - displayedHeightPx) / 2

    return VideoDisplayRect(
        widthDp = displayedWidthPx / density,
        heightDp = displayedHeightPx / density,
        offsetXDp = offsetX / density,
        offsetYDp = offsetY / density
    )
}

///**
// * Returns the current screen dimensions in dp.
// *
// * @return A [Pair] of [Int] values representing the screen width and height in dp.
// */
//@Composable
//private fun getScreenDimensions(): Pair<Int, Int> {
//    val context = LocalContext.current
//    val displayMetrics: DisplayMetrics = context.resources.displayMetrics
//    val widthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
//    val heightDp = (displayMetrics.heightPixels / displayMetrics.density).toInt()
//    return Pair(widthDp, heightDp)
//}



private data class SubtitleAnchorInfo(
    val x: Float,
    val y: Float,
    val positionAnchor: Int,
    val lineAnchor: Int
)