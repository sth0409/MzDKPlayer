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
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.EOFException
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.math.min

@UnstableApi
class SmbDataSource(
    private val config: SmbDataSourceConfig = SmbDataSourceConfig()
) : BaseDataSource(/* isNetwork= */ true) {

    companion object {
        private const val TAG = "SmbDataSource"

        // --- 全局静态缓存 (关键修改) ---
        // 将连接对象设为静态，这样不同的 DataSource 实例（ExoPlayer 会创建多个）
        // 就能复用同一个 TCP 连接，不用每次 Seek 都重新握手。
        private var sharedSmbClient: SMBClient? = null
        private var cachedConnection: Connection? = null
        private var cachedSession: Session? = null
        private var cachedShare: DiskShare? = null

        // 记录当前的 Host，如果 Host 变了（比如切视频），需要重连
        private var currentHost: String? = null

        /**
         * 静态释放方法：供外部（如 VideoPlayerScreen）调用，彻底关闭连接
         */
        fun releaseGlobalResources() {
            Log.i(TAG, "Releasing GLOBAL SMB resources...")
            try {
                cachedShare?.close()
                cachedSession?.close()
                cachedConnection?.close()
                // sharedSmbClient 通常建议保留，或者也在这里关掉
                // sharedSmbClient?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing global resources", e)
            } finally {
                cachedShare = null
                cachedSession = null
                cachedConnection = null
                currentHost = null
                // 如果你确定下次不再使用 SMBClient，可以置空
                // sharedSmbClient = null
            }
        }
    }

    // --- 实例变量 ---
    private var dataSpec: DataSpec? = null
    private var file: File? = null // 文件句柄必须是实例级的
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0
    private var opened = false

    private val readBuffer: ByteArray = ByteArray(config.bufferSizeBytes)
    private var bufferPosition: Int = 0
    private var bufferLimit: Int = 0
    private var currentFileOffset: Long = 0

    // SMB 协议配置
    private val PREFERRED_SMB_DIALECTS = EnumSet.of(
        SMB2Dialect.SMB_3_1_1,
        SMB2Dialect.SMB_3_0,
        SMB2Dialect.SMB_3_0_2,
        SMB2Dialect.SMB_2XX,
        SMB2Dialect.SMB_2_1
    )

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.bytesRead = 0
        this.bytesToRead = 0
        transferInitializing(dataSpec)

        try {
            val uri = dataSpec.uri
            // 1. 获取全局复用的资源
            ensureGlobalConnection(uri)

            // 2. 打开具体的文件
            val path = uri.path ?: throw IOException("无效路径")
            val pathSegments = path.split("/").filter { it.isNotEmpty() }
            if (pathSegments.size < 2) throw IOException("路径必须包含共享名和文件路径")
            val filePath = pathSegments.drop(1).joinToString("/")

            // 注意：这里使用全局的 cachedShare
            file = cachedShare?.openFile(
                filePath,
                setOf(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            ) ?: throw IOException("无法打开文件: $filePath")

            // 3. 获取大小、验证范围 (同之前逻辑)
            val fileInfo = file!!.fileInformation.standardInformation
            val fileLength = fileInfo.endOfFile
            val position = dataSpec.position

            if (position > fileLength) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }

            bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - position
            }

            currentFileOffset = position
            bufferPosition = 0
            bufferLimit = 0
            opened = true
            transferStarted(dataSpec)

            return bytesToRead
        } catch (e: Exception) {
            // 如果打开失败，且不是 EOF，可能是连接断了，尝试重置全局连接
            if (e !is EOFException) {
                Log.w(TAG, "Open failed, clearing global cache. Error: ${e.message}")
                releaseGlobalResources() // 遇到严重错误，清理连接以便下次重试
            }
            throw IOException("Open error: ${e.message}", e)
        }
    }

    /**
     * 确保全局连接可用
     */
    private fun ensureGlobalConnection(uri: Uri) {
        val host = uri.host ?: throw IOException("Host missing")
        val (user, pass) = parseUserInfo(uri)

        // 检查是否需要重新连接 (Host 变了，或者连接断了)
        if (cachedConnection == null || !cachedConnection!!.isConnected || currentHost != host) {
            Log.d(TAG, "Creating NEW connection to $host")
            // 1. 清理旧连接
            releaseGlobalResources()

            // 2. 初始化 Client
            if (sharedSmbClient == null) {
                val clientConfig = SmbConfig.builder()
                    .withDialects(PREFERRED_SMB_DIALECTS)
                    .withMultiProtocolNegotiate(true)
                    .withBufferSize(config.smbBufferSizeBytes)
                    .withSoTimeout(config.soTimeoutMs)
                    .withTimeout(60_000, TimeUnit.MILLISECONDS)
                    .build()
                sharedSmbClient = SMBClient(clientConfig)
            }

            // 3. 建立连接
            cachedConnection = sharedSmbClient!!.connect(host)
            currentHost = host

            // 4. 认证
            val authContext = AuthenticationContext(user, pass.toCharArray(), "")
            cachedSession = cachedConnection!!.authenticate(authContext)
        }

        // 检查 Share 是否需要重新连接
        val shareName = uri.path?.split("/")?.filter { it.isNotEmpty() }?.get(0)
            ?: throw IOException("No share name")

        if (cachedShare == null || cachedShare?.smbPath?.shareName != shareName) {
            cachedShare?.close()
            cachedShare = cachedSession?.connectShare(shareName) as? DiskShare
                ?: throw IOException("Connect share failed: $shareName")
        }
    }

    // ... read(), readInternal(), refillBuffer() 逻辑保持不变 ...
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
            min(bytesToRead - bytesRead, readLength.toLong()).toInt()
        }
        val bytesReadNow = readInternal(buffer, offset, bytesToReadNow)
        if (bytesReadNow == -1) {
            if (bytesToRead != C.LENGTH_UNSET.toLong()) throw EOFException()
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += bytesReadNow
        bytesTransferred(bytesReadNow)
        return bytesReadNow
    }

    private fun readInternal(targetBuffer: ByteArray, offset: Int, length: Int): Int {
        if (bufferLimit > bufferPosition) {
            val bytesInBuf = bufferLimit - bufferPosition
            val copyLen = min(bytesInBuf, length)
            System.arraycopy(readBuffer, bufferPosition, targetBuffer, offset, copyLen)
            bufferPosition += copyLen
            return copyLen
        }
        try {
            val bytesFilled = refillBuffer()
            if (bytesFilled <= 0) return -1
            return readInternal(targetBuffer, offset, length)
        } catch (e: Exception) {
            throw IOException("Read error: ${e.message}", e)
        }
    }

    private fun refillBuffer(): Int {
        val f = file ?: return -1
        val readSize = config.bufferSizeBytes
        val bytesReadFromFile = f.read(readBuffer, currentFileOffset, 0, readSize)
        if (bytesReadFromFile <= 0) return -1
        bufferPosition = 0
        bufferLimit = bytesReadFromFile
        currentFileOffset += bytesReadFromFile
        return bytesReadFromFile
    }

    private fun parseUserInfo(uri: Uri): Pair<String, String> {
        val userInfo = uri.userInfo
        if (userInfo.isNullOrEmpty()) return Pair("guest", "")
        val parts = userInfo.split(":")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(parts[0], "")
    }

    override fun getUri(): Uri? = dataSpec?.uri

    /**
     * close() 方法由 ExoPlayer 调用。
     * 关键点：只关闭当前文件句柄 (File)，保留 Connection/Session/Share 给下次复用。
     */
    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        try {
            file?.close() // 只关文件
        } catch (e: Exception) {
            Log.w(TAG, "Error closing file", e)
        } finally {
            file = null
            dataSpec = null
            // 不要在这里调用 releaseGlobalResources()！
        }
    }
}

// 保持原来的 Factory 不变
@UnstableApi
class SmbDataSourceFactory(private val config: SmbDataSourceConfig = SmbDataSourceConfig()) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return SmbDataSource(config)
    }
}

data class SmbDataSourceConfig(
    val bufferSizeBytes: Int = 8 * 1024 * 1024,
    val smbBufferSizeBytes: Int = 4 * 1024 * 1024,
    val soTimeoutMs: Int = 0,
    val minLogSpeedMBs: Double = 5.0
)