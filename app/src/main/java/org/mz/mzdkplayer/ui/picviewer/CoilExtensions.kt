package org.mz.mzdkplayer.ui.picviewer



import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult // 注意这里导入了具体的实现类
import coil3.request.Options
import okio.FileSystem
import okio.buffer
import okio.source
import org.mz.mzdkplayer.tool.SmbUtils
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

// 1. 数据包装类保持不变
data class RemoteMedia(
    val uri: String,
    val type: String // "SMB", "FTP", "WEBDAV", "NFS", "LOCAL"
)

// 2. 修正后的 Fetcher
class RemoteMediaFetcher(
    private val data: RemoteMedia,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val uri = data.uri.toUri()
        val inputStream: InputStream
        // 根据 type 调用不同的工具方法获取 InputStream
        withContext(Dispatchers.IO) {
            when (data.type) {
                "SMB" -> inputStream = SmbUtils.openSmbFileInputStream(uri, "pics")
                "FTP" -> inputStream = SmbUtils.openFtpFileInputStream(uri, "pics")
                "NFS" -> inputStream = SmbUtils.openNfsFileInputStream(uri, "pics")
                "WEBDAV" -> inputStream = SmbUtils.openWebDavFileInputStream(uri, "pics")

                // 【调用 SmbUtils.openLocalFileInputStream】
                "LOCAL" -> inputStream = SmbUtils.openLocalFileInputStream(uri)

                // 【调用 SmbUtils.openHttpFileInputStream】
                // 你的视频数据源处理逻辑中，WEBDAV 也使用了 http/https scheme，但 type 为 WEBDAV
                // 这里的 "HTTP" type 专门用于处理普通的 HTTP/HTTPS 链接
                "HTTP" -> inputStream = SmbUtils.openHTTPLinkXmlInputStream(uri.toString(), "pics")

                else -> throw IllegalArgumentException("Unsupported DataSource type: ${data.type}")
            }
        }
        // 将 InputStream 转换为 BufferedSource
        val bufferedSource = inputStream.source().buffer()

        // 3. 关键修正点：
        // (1) 使用 ImageSource 工厂函数，必须传入 FileSystem.SYSTEM
        // (2) 返回 SourceFetchResult 实例
        return SourceFetchResult(
            source = ImageSource(
                source = bufferedSource,
                fileSystem = FileSystem.SYSTEM // 必传参数，用于创建临时文件
            ),
            mimeType = "image/jpeg", // 你也可以根据文件名后缀判断，或者传 null 让 Coil 自己猜
            dataSource = DataSource.NETWORK
        )
    }

    class Factory : Fetcher.Factory<RemoteMedia> {
        override fun create(
            data: RemoteMedia,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return RemoteMediaFetcher(data, options)
        }
    }
}