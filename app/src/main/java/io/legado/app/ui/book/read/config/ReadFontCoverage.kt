package io.legado.app.ui.book.read.config

import android.net.Uri
import android.util.LruCache
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadValueScope
import splitties.init.appCtx
import java.io.File
import java.nio.ByteBuffer

/**
 * 字体设置页的诊断：判断某个脚本维度的有效字库是否真的覆盖该脚本的代表字形。
 *
 * 直接读 font cmap，而不是 Paint.hasGlyph / document.fonts.check()——后两者会沿系统
 * fallback 或 unicode-range 命中，无法证明“这个字体文件本身有该字形”。
 *
 * 只回答“有没有字形”，不负责加载失败；加载失败由 EpubScriptFontHealth 处理。
 * null 表示无法判断（platform/publisher/空字体标记/解析失败）。
 */
internal object ReadFontCoverage {
    private data class Key(val value: String, val scope: ReadValueScope, val version: Long)

    private val cache = LruCache<Key, Boolean>(32)

    fun covers(value: String?, scope: ReadValueScope): Boolean? {
        if (value.isNullOrBlank()) return null
        if (value == "platform" || value == "publisher") return null
        val path = when {
            value.startsWith("system:") -> ReadBookConfig.systemFontFile(value) ?: return false
            else -> value
        }
        val version = runCatching {
            val file = File(path)
            file.lastModified() xor file.length()
        }.getOrDefault(0L)
        val key = Key(value, scope, version)
        synchronized(cache) { cache.get(key) }?.let { return it }
        val cmap = runCatching { readCmap(path) }.getOrNull() ?: return null
        val result = samples(scope).any { cmap.contains(it.codePointAt(0)) }
        synchronized(cache) { cache.put(key, result) }
        return result
    }

    private fun readCmap(path: String): Cmap? {
        val bytes = readBytes(path) ?: return null
        if (bytes.size < 12) return null
        val buffer = ByteBuffer.wrap(bytes)
        var base = 0
        if (buffer.getInt(0) == 0x74746366) { // ttcf
            if (buffer.getInt(8) < 1) return null
            base = buffer.getInt(12)
        }
        if (base < 0 || base + 12 > bytes.size) return null
        val version = buffer.getInt(base)
        if (version != 0x00010000 && version != 0x4F54544F && version != 0x74727565) return null
        val tableCount = ushort(buffer, base + 4)
        if (tableCount !in 1..4095) return null
        var cmapOffset = -1
        repeat(tableCount) { index ->
            val record = base + 12 + index * 16
            if (record + 16 > bytes.size) return null
            if (buffer.getInt(record) == 0x636D6170) { // cmap
                cmapOffset = buffer.getInt(record + 8)
            }
        }
        if (cmapOffset < 0 || cmapOffset + 4 > bytes.size) return null
        val count = ushort(buffer, cmapOffset + 2)
        var format4 = -1
        var format12 = -1
        repeat(count) { index ->
            val record = cmapOffset + 4 + index * 8
            if (record + 8 > bytes.size) return@repeat
            val platform = ushort(buffer, record)
            val encoding = ushort(buffer, record + 2)
            val offset = cmapOffset + buffer.getInt(record + 4)
            if (offset + 2 > bytes.size) return@repeat
            val format = ushort(buffer, offset)
            when {
                format == 12 && (platform == 0 || platform == 3 && encoding == 10) -> format12 = offset
                format == 4 && (platform == 0 || platform == 3 && encoding == 1) && format4 < 0 -> format4 = offset
            }
        }
        return when {
            format12 >= 0 -> parseFormat12(buffer, bytes.size, format12)
            format4 >= 0 -> parseFormat4(buffer, bytes.size, format4)
            else -> null
        }
    }

    private fun parseFormat12(buffer: ByteBuffer, size: Int, offset: Int): Cmap? {
        if (offset + 16 > size) return null
        val groups = buffer.getInt(offset + 12)
        if (groups < 0 || offset + 16L + groups * 12L > size) return null
        return object : Cmap {
            override fun contains(codePoint: Int): Boolean {
                var low = 0
                var high = groups - 1
                while (low <= high) {
                    val mid = (low + high) ushr 1
                    val at = offset + 16 + mid * 12
                    val start = buffer.getInt(at)
                    val end = buffer.getInt(at + 4)
                    when {
                        codePoint < start -> high = mid - 1
                        codePoint > end -> low = mid + 1
                        else -> {
                            val glyph = buffer.getInt(at + 8) + (codePoint - start)
                            return glyph != 0
                        }
                    }
                }
                return false
            }
        }
    }

    private fun parseFormat4(buffer: ByteBuffer, size: Int, offset: Int): Cmap? {
        if (offset + 14 > size) return null
        val segCount = ushort(buffer, offset + 6) / 2
        if (segCount <= 0 || offset + 16L + segCount * 8L > size) return null
        val endCodes = offset + 14
        val startCodes = endCodes + segCount * 2 + 2
        val deltas = startCodes + segCount * 2
        val rangeOffsets = deltas + segCount * 2
        if (rangeOffsets + segCount * 2L > size) return null
        return object : Cmap {
            override fun contains(codePoint: Int): Boolean {
                if (codePoint > 0xFFFF) return false
                for (index in 0 until segCount) {
                    val end = ushort(buffer, endCodes + index * 2)
                    if (codePoint > end) continue
                    val start = ushort(buffer, startCodes + index * 2)
                    if (codePoint < start) return false
                    val delta = buffer.getShort(deltas + index * 2).toInt()
                    val rangeOffset = ushort(buffer, rangeOffsets + index * 2)
                    val glyph = if (rangeOffset == 0) {
                        (codePoint + delta) and 0xFFFF
                    } else {
                        val at = rangeOffsets + index * 2 + rangeOffset + (codePoint - start) * 2
                        if (at + 2 > size) return false
                        val raw = ushort(buffer, at)
                        if (raw == 0) 0 else (raw + delta) and 0xFFFF
                    }
                    return glyph != 0
                }
                return false
            }
        }
    }

    private fun readBytes(path: String): ByteArray? {
        val stream = when {
            path.startsWith("assets://") -> appCtx.assets.open(path.removePrefix("assets://"))
            path.startsWith("content://") -> appCtx.contentResolver.openInputStream(Uri.parse(path))
            else -> File(path).inputStream()
        } ?: return null
        return stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > 64 * 1024 * 1024) return null
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }

    private fun ushort(buffer: ByteBuffer, at: Int): Int = buffer.getShort(at).toInt() and 0xffff

    private fun samples(scope: ReadValueScope): List<String> = when (scope) {
        ReadValueScope.CJK -> listOf("你", "好", "的", "一")
        ReadValueScope.LATIN -> listOf("A", "a")
        ReadValueScope.OTHER -> listOf("Α", "А", "א", "ا", "अ")
        ReadValueScope.DEFAULT -> listOf("A", "a")
    }

    private interface Cmap {
        fun contains(codePoint: Int): Boolean
    }
}
