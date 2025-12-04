package org.mz.mzdkplayer.tool

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * 优化后的 FTP 数据源
 * * 核心优化：
 * 1. 实现了 FTPClient 的静态复用 (Keep-Alive)。
 * 2. close() 时只关闭数据流并完成 Pending Command，不断开控制连接。
 */
@UnstableApi
class FtpDataSource : BaseDataSource(/* isNetwork= */ true) {

    companion object {
        private const val TAG = "FtpDataSource"
        private const val BUFFER_SIZE = 512 * 1024
        private const val CONNECTION_TIMEOUT_MS = 30000

        // --- 全局静态缓存 ---
        private var cachedFtpClient: FTPClient? = null
        private var currentHost: String? = null
        private var currentUser: String? = null
        private var transferStarted: Boolean = false
        /**
         * 静态释放方法：供 VideoPlayerScreen 调用
         * 彻底断开 FTP 连接。
         */
        fun releaseGlobalResources() {
            Log.i(TAG, "Releasing GLOBAL FTP resources...")
            try {
                if (cachedFtpClient?.isConnected == true) {
                    try { cachedFtpClient?.logout() } catch (ignored: Exception) {}
                    try { cachedFtpClient?.disconnect() } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing global FTP resources", e)
            } finally {
                cachedFtpClient = null
                currentHost = null
                currentUser = null
            }
        }
    }

    // --- 实例变量 ---
    private var dataSpec: DataSpec? = null
    private var fileInputStream: InputStream? = null // 当前的数据流

    private var bytesRead: Long = 0
    private var bytesToRead: Long = 0
    private var opened = false

    private var startTimeMs: Long = 0L
    private var totalBytesTransferred: Long = 0L

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        Log.d(TAG, "Opening: ${dataSpec.uri}")
        this.dataSpec = dataSpec
        bytesRead = 0
        bytesToRead = 0
        transferInitializing(dataSpec)

        try {
            // 1. 获取/复用全局连接
            ensureGlobalConnection(dataSpec)

            // 2. 获取文件大小 (使用复用的连接)
            val fileLength = getFileLength(dataSpec.uri.path!!)
            val startPosition = dataSpec.position

            if (startPosition > fileLength) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }

            bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - startPosition
            }

            // 3. 设置断点续传位置
            if (startPosition > 0) {
                cachedFtpClient?.restartOffset = startPosition
            }

            // 4. 打开数据流
            // 注意：使用全局 cachedFtpClient
            fileInputStream = cachedFtpClient?.retrieveFileStream(dataSpec.uri.path)
            val reply = cachedFtpClient?.replyCode ?: 0

            if (fileInputStream == null) {
                throw IOException("无法打开 FTP 文件流。回复码: $reply")
            }

            opened = true
            transferStarted = true
            transferStarted(dataSpec)

            startTimeMs = System.currentTimeMillis()
            return bytesToRead

        } catch (e: Exception) {
            // 发生错误，为了安全，清理全局连接（以便重试）
            closeConnectionQuietly(forceReleaseGlobal = true)
            if (e is IOException) throw e
            throw IOException("Open error: ${e.message}", e)
        }
    }

    /**
     * 确保全局 FTPClient 可用且连接正确
     */
    private fun ensureGlobalConnection(dataSpec: DataSpec) {
        val uri = dataSpec.uri
        val host = uri.host ?: throw IOException("Missing host")
        val port = if (uri.port != -1) uri.port else 21
        val (username, password) = parseUserInfo(uri)

        // 检查复用条件：Client 存在 + 已连接 + Host 没变 + User 没变
        if (cachedFtpClient != null &&
            cachedFtpClient!!.isConnected &&
            currentHost == host &&
            currentUser == username) {

            // 发送 NoOp 检查连接是否假死
            try {
                if (cachedFtpClient!!.sendNoOp()) {
                    Log.d(TAG, "Reusing existing FTP connection to $host")
                    // 重新设置模式，防止被之前的操作修改
                    cachedFtpClient!!.setFileType(FTP.BINARY_FILE_TYPE)
                    cachedFtpClient!!.enterLocalPassiveMode()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cached connection stale, reconnecting...")
            }
        }

        // 无法复用，彻底清理旧的并新建
        releaseGlobalResources()

        Log.d(TAG, "Creating NEW FTP connection to $host")
        val client = FTPClient()
        client.controlEncoding = "UTF-8"
        client.bufferSize = BUFFER_SIZE
        client.connectTimeout = CONNECTION_TIMEOUT_MS

        client.connect(host, port)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            throw IOException("Connect failed: ${client.replyString}")
        }

        if (!client.login(username, password)) {
            throw IOException("Login failed: ${client.replyString}")
        }

        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.enterLocalPassiveMode()

        // 更新全局缓存
        cachedFtpClient = client
        currentHost = host
        currentUser = username
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = bytesToRead - bytesRead
            if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        }

        val bytesToReadNow = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            min(readLength.toLong(), bytesToRead - bytesRead).toInt()
        }
        if (bytesToReadNow == 0) return C.RESULT_END_OF_INPUT

        val stream = fileInputStream ?: throw IOException("Stream is null")

        try {
            val bytesReadNow = stream.read(buffer, offset, bytesToReadNow)
            if (bytesReadNow == -1) {
                if (bytesToRead != C.LENGTH_UNSET.toLong()) throw EOFException()
                return C.RESULT_END_OF_INPUT
            }

            bytesRead += bytesReadNow
            totalBytesTransferred += bytesReadNow
            bytesTransferred(bytesReadNow)

            // 简单的日志监控，避免太频繁
            if (bytesReadNow > 0 && totalBytesTransferred % (2 * 1024 * 1024) == 0L) {
                val time = System.currentTimeMillis() - startTimeMs
                if (time > 0) {
                    val speed = (totalBytesTransferred * 1000.0 / time) / 1024 / 1024
                    Log.d(TAG, "Speed: %.2f MB/s".format(speed))
                }
            }

            return bytesReadNow
        } catch (e: IOException) {
            throw IOException("Read error", e)
        }
    }

    private fun getFileLength(path: String): Long {
        // 优先用 mlistFile，某些服务器不支持，则 fallback 到 listFiles
        val client = cachedFtpClient ?: throw IOException("Client not connected")
        try {
            val mFile = client.mlistFile(path)
            if (mFile != null) return mFile.size

            val files = client.listFiles(path)
            if (files != null && files.isNotEmpty()) return files[0].size
        } catch (e: Exception) {
            // ignore
        }
        // 如果无法获取大小，返回 UNSET? 或者抛错。ExoPlayer 通常需要知道大小。
        // 为了健壮性，这里尝试再次获取，如果不行抛错
        throw IOException("Cannot determine file size for $path")
    }

    override fun getUri(): Uri? = dataSpec?.uri

    /**
     * Close:
     * 1. 关闭 InputStream (数据传输结束)。
     * 2. 调用 completePendingCommand() (这一步至关重要，否则 FTP 控制连接会卡死)。
     * 3. **保留** cachedFtpClient 不断开。
     */
    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        closeConnectionQuietly(forceReleaseGlobal = false)
        dataSpec = null
    }

    private fun closeConnectionQuietly(forceReleaseGlobal: Boolean) {
        // 1. 关闭数据流
        try {
            fileInputStream?.close()
        } catch (ignored: Exception) {}
        fileInputStream = null

        if (forceReleaseGlobal) {
            releaseGlobalResources()
        } else {
            // 2. 尝试完成事务，以便复用连接
            // Apache FTPClient 要求：InputStream 关闭后，必须调用 completePendingCommand
            try {
                if (cachedFtpClient?.isConnected == true) {
                    if (!cachedFtpClient!!.completePendingCommand()) {
                        Log.w(TAG, "Failed to complete pending command, connection might be dead.")
                        releaseGlobalResources() // 状态不对，下次只能重连
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error completing FTP command", e)
                releaseGlobalResources()
            }
        }
    }

    private fun parseUserInfo(uri: Uri): Pair<String, String> {
        val userInfo = uri.userInfo
        if (userInfo.isNullOrEmpty()) return Pair("anonymous", "")
        val parts = userInfo.split(":")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(parts[0], "")
    }
}

@UnstableApi
class FtpDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return FtpDataSource()
    }
}