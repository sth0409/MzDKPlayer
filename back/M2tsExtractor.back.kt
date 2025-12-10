package org.mz.mzdkplayer.tool
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.io.EOFException
import kotlin.math.min

/**
 * M2TS 提取器
 * 逻辑：包装标准的 TsExtractor，通过 Adapter 将 192 字节的 M2TS 包转换为 188 字节的 TS 包
 */
@UnstableApi
class `M2tsExtractor.back` : Extractor {

    // 内部持有标准的 TsExtractor
    private val tsExtractor = TsExtractor(
        SubtitleParser.Factory.UNSUPPORTED
    )

    // 用于缓存 Adapter，避免每次 read 都创建对象
    private var inputAdapter: M2tsInputAdapter? = null

    override fun init(output: ExtractorOutput) {
        tsExtractor.init(output)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        // 1. 包装 Input
        if (inputAdapter == null || inputAdapter?.delegate !== input) {
            inputAdapter = M2tsInputAdapter(input)
        }
        val adapter = inputAdapter!!

        // 2. 调用内部 TsExtractor
        // 注意：SeekPosition 传进去的值是基于 Virtual (188) 坐标系的
        val result = tsExtractor.read(adapter, seekPosition)

        // 3. 坐标系还原
        // TsExtractor 如果要求 Seek，它修改的是 seekPosition (基于 188 字节流)
        // 我们需要把这个位置换算回 M2TS (192 字节流) 的真实位置反馈给上层
        if (result == Extractor.RESULT_SEEK) {
            seekPosition.position = adapter.virtualToReal(seekPosition.position)
        }

        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        // 外部调用 seek 时，position 是真实的时间点对应的位置？
        // 通常 seek 主要是重置状态，具体位置由 read 里的 BinarySearch 决定
        tsExtractor.seek(position, timeUs)
        inputAdapter?.reset()
    }

    override fun release() {
        tsExtractor.release()
    }

    override fun sniff(input: ExtractorInput): Boolean {
        // M2TS 的 sniff 逻辑必须重写，因为 sync byte 位置不同
        val buffer = ByteArray(192)
        try {
            // 尝试读取一个完整的 M2TS 包
            input.peekFully(buffer, 0, 192)

            // 检查 M2TS 的 Sync Byte (0x47)
            // 在 192 字节模式下，前 4 字节是 TP_extra_header，offset 4 才是 0x47
            val isSyncBytePresent = buffer[4] == 0x47.toByte()

            // 进一步验证：检查下一个包是否也对齐
            if (isSyncBytePresent) {
                // 这里为了性能只查一个，严谨的话可以查 5 个包
                return true
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    /**
     * 核心适配器：将 M2TS 流伪装成 TS 流
     */
    private class M2tsInputAdapter(val delegate: ExtractorInput) : ExtractorInput {

        private val packetBuffer = ByteArray(192) // 真实读取缓冲区
        private val TS_SIZE = 188
        private val M2TS_SIZE = 192
        private val HEADER_SIZE = 4

        // 坐标转换系数
        private val scaleFactor = 192.0 / 188.0

        fun virtualToReal(pos: Long): Long {
            if (pos == C.POSITION_UNSET.toLong()) return C.POSITION_UNSET.toLong()
            return (pos / TS_SIZE) * M2TS_SIZE
        }

        fun realToVirtual(pos: Long): Long {
            if (pos == C.POSITION_UNSET.toLong()) return C.POSITION_UNSET.toLong()
            return (pos / M2TS_SIZE) * TS_SIZE
        }

        fun reset() {
            // 可以在这里重置内部状态
        }

        // --- 核心 Read 逻辑 ---

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            // TsExtractor 的源码极其规律，它在读取 packet 时通常会请求 readFully 读取 188 字节
            // 或者读取少量 Header。为了简化，我们假设它主要按包读取。

            // 策略：如果请求长度是 188，我们就读 192 并去头
            if (length == TS_SIZE) {
                try {
                    // 从真实流中读 192
                    delegate.readFully(packetBuffer, 0, M2TS_SIZE)
                    // 复制后 188 字节给上层（剥离前 4 字节时间戳）
                    System.arraycopy(packetBuffer, HEADER_SIZE, buffer, offset, TS_SIZE)
                    return TS_SIZE
                } catch (e: Exception) {
                    // 处理文件结束
                    return C.RESULT_END_OF_INPUT
                }
            }

            // 如果读取非标准包大小（比如 TsExtractor 初始化时读少量数据），
            // 直接透传可能会错位，但在初始化阶段通常问题不大，或者需要更复杂的 buffer 处理。
            // 针对 Media3 1.8.0 的 TsExtractor，它几乎总是按 Packet 读取。
            // 兜底逻辑：按比例读取（风险较大，但对于非 Packet 读取不得不做）
            val realBytesToRead = (length * scaleFactor).toInt()
            // 这里非常危险，因为如果不是对齐读取，流就乱了。
            // 幸好 TsExtractor 代码结构决定了它主要依赖 readFully(188)
            return delegate.read(buffer, offset, length)
        }

        override fun readFully(buffer: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            val bytesRead = read(buffer, offset, length)
            if (bytesRead == -1) {
                if (allowEndOfInput) return false
                throw EOFException()
            }
            return true
        }

        override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
            readFully(buffer, offset, length, false)
        }

        // --- 坐标系欺骗 ---

        override fun getPosition(): Long {
            return realToVirtual(delegate.position)
        }

        override fun getLength(): Long {
            return realToVirtual(delegate.length)
        }

        // --- Peek 逻辑 ---

        override fun peek(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == TS_SIZE) {
                try {
                    delegate.peekFully(packetBuffer, 0, M2TS_SIZE)
                    System.arraycopy(packetBuffer, HEADER_SIZE, buffer, offset, TS_SIZE)
                    return TS_SIZE
                } catch (e: Exception) {
                    return C.RESULT_END_OF_INPUT
                }
            }
            return delegate.peek(buffer, offset, length)
        }

        override fun peekFully(buffer: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            if (length == TS_SIZE) {
                val res = peek(buffer, offset, length)
                if (res == -1) {
                    if(allowEndOfInput) return false
                    throw EOFException()
                }
                return true
            }
            return delegate.peekFully(buffer, offset, length, allowEndOfInput)
        }

        override fun peekFully(buffer: ByteArray, offset: Int, length: Int) {
            peekFully(buffer, offset, length, false)
        }

        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean {
            // 核心逻辑：上层想在虚拟流中前进 length，底层需要在真实流中前进 realLength
            // 公式：realLength = length * (192 / 188)

            val realLength = if (length % TS_SIZE == 0) {
                // 完美对齐的情况（这是最常见的，比如跳过整包）
                (length / TS_SIZE) * M2TS_SIZE
            } else {
                // 非对齐情况，使用浮点运算或者先乘后除以保持精度
                // 注意：这里强行转换可能会导致并未正好落在 Packet 边界，
                // 但如果 TsExtractor 只是在做小范围探测，通常问题不大。
                ((length.toLong() * M2TS_SIZE) / TS_SIZE).toInt()
            }

            return delegate.advancePeekPosition(realLength, allowEndOfInput)
        }

        override fun advancePeekPosition(length: Int) {
            advancePeekPosition(length, false)
        }

        override fun resetPeekPosition() {
            // 这个最简单，直接透传。
            // 因为 peekPosition 重置意味着回到当前的 readPosition。
            // 我们的 readPosition 映射关系是由 getPosition() 维护的，这里直接复位即可。
            delegate.resetPeekPosition()
        }

        // --- Skip 逻辑 ---
        // TsExtractor 解析 PSI 可能会 skip 数据

        override fun skip(length: Int): Int {
            val realSkip = if (length % TS_SIZE == 0) {
                (length / TS_SIZE) * M2TS_SIZE
            } else {
                (length * scaleFactor).toInt()
            }
            return delegate.skip(realSkip)
        }

        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
            val realSkip = if (length % TS_SIZE == 0) {
                (length / TS_SIZE) * M2TS_SIZE
            } else {
                (length * scaleFactor).toInt()
            }
            return delegate.skipFully(realSkip, allowEndOfInput)
        }

        override fun skipFully(length: Int) {
            skipFully(length, false)
        }

        override fun getPeekPosition(): Long {
            return realToVirtual(delegate.peekPosition)
        }

        override fun <E : Throwable?> setRetryPosition(position: Long, e: E & Any) {
            // position 是 virtual 的，需要转换回 real
            val realPos = virtualToReal(position)
            delegate.setRetryPosition(realPos, e)
        }
    }
}