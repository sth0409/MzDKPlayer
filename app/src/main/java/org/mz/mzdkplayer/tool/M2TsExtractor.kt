package org.mz.mzdkplayer.tool

import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

// 常量定义
private const val TS_PACKET_SIZE = 188
private const val M2TS_PACKET_SIZE = 192
private const val M2TS_HEADER_SIZE = 4

@UnstableApi
class M2tsExtractor : Extractor {

    // 复用官方的 TsExtractor
    // 初始化 TsExtractor，使用默认的 PayloadReaderFactory
    // 你可以根据需要调整模式，例如 MODE_HLS 或 MODE_MULTI_PMT
    private val tsExtractor: TsExtractor = TsExtractor(

    )

    // 用于转换 Seek 位置的临时变量
    private val virtualPositionHolder = PositionHolder()

    // 我们自定义的 Input 包装器
    private var m2tsInputAdapter: M2tsReformattingExtractorInput? = null

    override fun init(output: ExtractorOutput) {
        tsExtractor.init(output)
    }

    override fun sniff(input: ExtractorInput): Boolean {
        val scratch = ByteArray(M2TS_PACKET_SIZE * 5) // 检查 5 个包
        input.peekFully(scratch, 0, scratch.size)

        for (i in 0 until 5) {
            // 每个包的第 4 个索引（即第 5 个字节）必须是 0x47
            if (scratch[i * M2TS_PACKET_SIZE + 4] != 0x47.toByte()) {
                return false
            }
        }
        return true
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        // 1. 包装 Input，使其自动剥离 4 字节头
        if (m2tsInputAdapter == null) {
            // 第一次读取时，先找第一个 0x47 确定真正的起点
            val scratch = ByteArray(M2TS_PACKET_SIZE)
            input.peekFully(scratch, 0, M2TS_PACKET_SIZE)
            var firstSyncOffset = -1
            for (i in 0 until M2TS_PACKET_SIZE) {
                if (scratch[i] == 0x47.toByte()) {
                    // M2TS 的 0x47 应该在索引 4
                    firstSyncOffset = i - 4
                    break
                }
            }

            if (firstSyncOffset > 0) {
                input.skipFully(firstSyncOffset)
            }

            m2tsInputAdapter = M2tsReformattingExtractorInput(input)
        }
      //  if (m2tsInputAdapter == null || m2tsInputAdapter?.getOriginalInput() !== input) {
      //     m2tsInputAdapter = M2tsReformattingExtractorInput(input)
       // }
        val adapter = m2tsInputAdapter!!

        // 2. 这里的逻辑稍微复杂：TsExtractor 返回的 SeekPosition 是基于 188 字节流的 "虚拟位置"
        // 我们需要把它转换回 M2TS 的 "真实位置" 告诉播放器

        val result = tsExtractor.read(adapter, virtualPositionHolder)

        if (result == Extractor.RESULT_SEEK) {
            // 坐标转换：虚拟位置 -> 真实位置
            val virtualPos = virtualPositionHolder.position
            val realPos = convertVirtualToRealPosition(virtualPos)
            seekPosition.position = realPos
        }

        return result
    }

    override fun seek(position: Long, timeUs: Long) {
        // 坐标转换：真实位置 -> 虚拟位置
        // 当播放器调用 seek 时，它给的是文件里的真实偏移量
        // 但 TsExtractor 需要知道的是剥离头之后的偏移量
        val virtualPos = if (position == 0L) 0L else convertRealToVirtualPosition(position)

        // 重置 Adapter 状态
        m2tsInputAdapter?.setVirtualPosition(virtualPos)

        tsExtractor.seek(virtualPos, timeUs)
    }

    override fun release() {
        tsExtractor.release()
    }

    // --- 坐标转换工具方法 ---

    private fun convertVirtualToRealPosition(virtualPos: Long): Long {
        val packetIndex = virtualPos / TS_PACKET_SIZE
        val offsetInPacket = virtualPos % TS_PACKET_SIZE
        return (packetIndex * M2TS_PACKET_SIZE) + M2TS_HEADER_SIZE + offsetInPacket
    }

    private fun convertRealToVirtualPosition(realPos: Long): Long {
        val packetIndex = realPos / M2TS_PACKET_SIZE
        // 减去 4 字节头
        var offsetInPacket = (realPos % M2TS_PACKET_SIZE) - M2TS_HEADER_SIZE
        if (offsetInPacket < 0) offsetInPacket = 0 // 防御性编程
        return (packetIndex * TS_PACKET_SIZE) + offsetInPacket
    }
}