package org.mz.mzdkplayer.tool

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import java.io.EOFException
import kotlin.math.min

/**
 * 一个适配器，用于将 M2TS 流 (192 字节包，4 字节头 + 188 字节数据)
 * 伪装成标准的 TS 流 (188 字节包) 供 TsExtractor 读取。
 *
 * 核心逻辑：
 * 1. 在 Read/Skip 时，每读取 188 字节 payload，自动跳过物理流的 4 字节头。
 * 2. 在 Peek 时，每预览 188 字节 payload，自动推进物理流的 peek 指针跳过 4 字节头。
 */
@UnstableApi
class M2tsReformattingExtractorInput(
    private val realInput: ExtractorInput
) : ExtractorInput {

    private companion object {
        const val TS_PACKET_SIZE = 188
        const val M2TS_PACKET_SIZE = 192
        const val M2TS_HEADER_SIZE = 4
    }

    // 当前虚拟流的位置 (即 TsExtractor 认为它读到的位置)
    private var currentVirtualPosition: Long = 0L

    // 自上次 resetPeekPosition 以来，逻辑上 Peek 了多少字节
    private var logicalBytesPeeked: Int = 0

    // 用于 skip 操作的临时缓存
    private val scratchBuffer = ByteArray(4096)

    // -------------------------------------------------------------------------
    // Read 相关 (消耗数据，移动指针)
    // -------------------------------------------------------------------------

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // 关键：一旦发生 Read 操作，之前的 Peek 状态就失效了，必须重置
        logicalBytesPeeked = 0

        var bytesRead = 0
        while (bytesRead < length) {
            val offsetInPacket = (currentVirtualPosition % TS_PACKET_SIZE).toInt()

            // 1. 如果处于新包的开始，必须在真实流中跳过 M2TS 的 4 字节头
            if (offsetInPacket == 0) {
                try {
                    // 跳过 4 字节头 (TP_extra_header)
                    realInput.skipFully(M2TS_HEADER_SIZE)
                } catch (e: EOFException) {
                    // 如果连头都跳不过去（文件结束），返回已读数据或 -1
                    return if (bytesRead == 0) -1 else bytesRead
                }
            }

            // 2. 计算当前包还能读多少有效数据
            val bytesLeftInPacket = TS_PACKET_SIZE - offsetInPacket
            val toRead = min(length - bytesRead, bytesLeftInPacket)

            // 3. 读取 Payload
            val readNow = realInput.read(buffer, offset + bytesRead, toRead)
            val firstByte = buffer[offset + bytesRead]
            if (offsetInPacket == 0 && firstByte != 0x47.toByte()) {
                android.util.Log.e("M2TS_DEBUG", "同步字错误！预期 0x47，实际: ${String.format("0x%02X", firstByte)} 虚拟位置: $currentVirtualPosition")
            }
            if (readNow == -1) {
                return if (bytesRead == 0) -1 else bytesRead
            }

            // 4. 更新状态
            currentVirtualPosition += readNow
            bytesRead += readNow
        }
        return bytesRead
    }

    override fun readFully(buffer: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
        var totalBytesRead = 0
        while (totalBytesRead < length) {
            val bytesRead = read(buffer, offset + totalBytesRead, length - totalBytesRead)
            if (bytesRead == -1) {
                if (allowEndOfInput) return false
                throw EOFException()
            }
            totalBytesRead += bytesRead
        }
        return true
    }

    override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
        readFully(buffer, offset, length, false)
    }

    override fun skip(length: Int): Int {
        // Skip 本质上就是 Read 并丢弃
        // 为了复用处理 header 的逻辑，我们直接调用 read 到临时 buffer
        val bytesToSkip = min(length, scratchBuffer.size)
        val bytesRead = read(scratchBuffer, 0, bytesToSkip)
        return bytesRead
    }

    override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
        var totalSkipped = 0
        while (totalSkipped < length) {
            val skipped = skip(length - totalSkipped)
            if (skipped == -1) {
                if (allowEndOfInput) return false
                throw EOFException()
            }
            totalSkipped += skipped
        }
        return true
    }

    override fun skipFully(length: Int) {
        skipFully(length, false)
    }

    // -------------------------------------------------------------------------
    // Peek 相关 (预览数据，不移动 Read 指针，但移动 Peek 指针)
    // -------------------------------------------------------------------------

    override fun peek(target: ByteArray, offset: Int, length: Int): Int {
        var totalBytesPeekedInThisCall = 0

        while (totalBytesPeekedInThisCall < length) {
            // 计算当前 Peek 指针的逻辑位置 (Read位置 + 已Peek偏移)
            val currentPeekPos = currentVirtualPosition + logicalBytesPeeked
            val offsetInPacket = (currentPeekPos % TS_PACKET_SIZE).toInt()

            // 1. 如果正好处于新包的开始，物理流的 Peek 指针需要跳过 4 字节头
            if (offsetInPacket == 0) {
                try {
                    // 告诉底层 Input：把 Peek 指针往前挪 4 步，但这不算数据
                    realInput.advancePeekPosition(M2TS_HEADER_SIZE)
                } catch (e: EOFException) {
                    return if (totalBytesPeekedInThisCall == 0) -1 else totalBytesPeekedInThisCall
                }
            }

            // 2. 计算当前包剩余可 Peek 的 Payload 长度
            val bytesLeftInPacket = TS_PACKET_SIZE - offsetInPacket
            val toPeek = min(length - totalBytesPeekedInThisCall, bytesLeftInPacket)

            // 3. 执行 Peek
            val peeked = realInput.peek(target, offset + totalBytesPeekedInThisCall, toPeek)
            if (peeked == -1) {
                return if (totalBytesPeekedInThisCall == 0) -1 else totalBytesPeekedInThisCall
            }

            // 4. 更新状态
            logicalBytesPeeked += peeked
            totalBytesPeekedInThisCall += peeked
        }
        return totalBytesPeekedInThisCall
    }

    override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
        var totalPeeked = 0
        while (totalPeeked < length) {
            val count = peek(target, offset + totalPeeked, length - totalPeeked)
            if (count == -1) {
                if (allowEndOfInput) return false
                throw EOFException()
            }
            totalPeeked += count
        }
        return true
    }

    override fun peekFully(target: ByteArray, offset: Int, length: Int) {
        peekFully(target, offset, length, false)
    }

    override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean {
        var totalAdvanced = 0

        while (totalAdvanced < length) {
            val currentPeekPos = currentVirtualPosition + logicalBytesPeeked
            val offsetInPacket = (currentPeekPos % TS_PACKET_SIZE).toInt()

            // 1. 遇到包头，先让 realInput 的 peek 指针跳过 4 字节
            if (offsetInPacket == 0) {
                try {
                    realInput.advancePeekPosition(M2TS_HEADER_SIZE)
                } catch (e: EOFException) {
                    if (allowEndOfInput) return false
                    throw EOFException()
                }
            }

            // 2. 计算本次可以推进多少 Payload
            val bytesLeftInPacket = TS_PACKET_SIZE - offsetInPacket
            val toAdvance = min(length - totalAdvanced, bytesLeftInPacket)

            // 3. 推进 realInput 的 peek 指针
            try {
                realInput.advancePeekPosition(toAdvance)
            } catch (e: EOFException) {
                if (allowEndOfInput) return false
                throw EOFException()
            }

            // 4. 更新状态
            logicalBytesPeeked += toAdvance
            totalAdvanced += toAdvance
        }
        return true
    }

    override fun advancePeekPosition(length: Int) {
        advancePeekPosition(length, false)
    }

    override fun resetPeekPosition() {
        realInput.resetPeekPosition()
        logicalBytesPeeked = 0
    }

    override fun getPeekPosition(): Long {
        return currentVirtualPosition + logicalBytesPeeked
    }

    // -------------------------------------------------------------------------
    // 其他辅助方法
    // -------------------------------------------------------------------------

    override fun getPosition(): Long {
        return currentVirtualPosition
    }

    override fun getLength(): Long {
        val realLen = realInput.length
        if (realLen <= 0) return realLen
        // 每一个 192 字节包含 188 字节有效数据
        return (realLen / 192) * 188
    }


    override fun <E : Throwable> setRetryPosition(position: Long, e: E) { realInput.setRetryPosition(position, e) }

    /**
     * 辅助方法：获取原始 Input 实例，用于比较是否发生变化
     */
    fun getOriginalInput(): ExtractorInput = realInput

    /**
     * 辅助方法：供 M2tsExtractor 在 Seek 时强制修正内部状态
     */
    fun setVirtualPosition(pos: Long) {
        currentVirtualPosition = pos
        logicalBytesPeeked = 0
    }
}