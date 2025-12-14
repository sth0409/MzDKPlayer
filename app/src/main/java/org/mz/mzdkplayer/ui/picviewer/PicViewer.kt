package org.mz.mzdkplayer.ui.picviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun PicViewerScreen(
    mediaUri: String,
    dataSourceType: String,
    fileName: String = "未知文件名",
    connectionName: String,
) {
    val context = LocalContext.current

    // 1. 创建自定义的 ImageLoader
    // 最好在 Application 级别全局单例提供，但为了局部使用，这里 remember 住也可以
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                // 注册我们刚才写的 Fetcher，放在最前面
                add(RemoteMediaFetcher.Factory())
            }
            .build()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 2. 使用 AsyncImage 加载图片
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(RemoteMedia(mediaUri, dataSourceType)) // 传入我们的包装类
                .crossfade(true) // 开启淡入淡出动画
                .build(),
            imageLoader = imageLoader, // 使用支持 SMB/FTP 的加载器
            contentDescription = fileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit // 自适应缩放，保持比例
        )
    }
}