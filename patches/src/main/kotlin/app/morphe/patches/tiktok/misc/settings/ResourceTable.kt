/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.settings

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads one resource id out of a `resources.arsc` by type and name.
 *
 * <p>A resource id is renumbered by every build, so writing one into a patch is writing the
 * build it was read from into the patch. The name is what survives: TikTok does not obfuscate
 * resource names, and `raw/icon_2pt_settings_stroke` is the same string on every build that has
 * the icon. This looks the id up at patch time from the table the APK actually carries.
 *
 * <p>Written rather than pulled in, because the whole of it is a walk through four chunk types
 * and the alternative is decoding every resource in the APK, which is the step that costs the
 * AMOLED patch a bigger heap than anything else in the bundle. Only what the lookup needs is
 * read: the package chunk, its two string pools, and the type chunks for the one type asked for.
 * Everything else is skipped by its declared size.
 */
internal class ResourceTable private constructor(private val buffer: ByteBuffer) {

    private class Package(
        val id: Int,
        val typeNames: List<String>,
        val keyNames: List<String>,
        val typeIdOffset: Int,
        val firstChild: Int,
        val end: Int,
    )

    private val packages: List<Package>

    init {
        require(u16(0) == RES_TABLE_TYPE) { "not a resource table: chunk type ${u16(0)}" }
        val tableHeaderSize = u16(2)
        val tableSize = u32(4)
        val found = mutableListOf<Package>()
        var at = tableHeaderSize
        while (at + 8 <= tableSize) {
            val type = u16(at)
            val size = u32(at + 4)
            require(size >= 8) { "chunk at $at declares size $size" }
            if (type == RES_TABLE_PACKAGE_TYPE) found += readPackage(at)
            at += size
        }
        packages = found
    }

    private fun readPackage(start: Int): Package {
        val headerSize = u16(start + 2)
        val size = u32(start + 4)
        val id = u32(start + 8)
        val typeStrings = u32(start + 8 + 4 + 256)
        val keyStrings = u32(start + 8 + 4 + 256 + 8)
        // The offset field arrived with a later header size; an older table has none and its
        // type ids start at one.
        val typeIdOffset = if (headerSize >= 288) u32(start + 8 + 4 + 256 + 16) else 0
        return Package(
            id = id,
            typeNames = readStringPool(start + typeStrings),
            keyNames = readStringPool(start + keyStrings),
            typeIdOffset = typeIdOffset,
            firstChild = start + headerSize,
            end = start + size,
        )
    }

    /**
     * The id of `type/name`, or null when no package carries it.
     *
     * <p>An entry can appear in several type chunks, one per configuration; they all share the
     * entry index, so the first is as good as any.
     */
    fun idOf(type: String, name: String): Int? {
        for (pkg in packages) {
            val typeIndex = pkg.typeNames.indexOf(type)
            if (typeIndex < 0) continue
            val typeId = typeIndex + 1 + pkg.typeIdOffset
            val keyIndex = pkg.keyNames.indexOf(name)
            if (keyIndex < 0) continue
            var at = pkg.firstChild
            while (at + 8 <= pkg.end) {
                val chunkType = u16(at)
                val chunkSize = u32(at + 4)
                require(chunkSize >= 8) { "chunk at $at declares size $chunkSize" }
                if (chunkType == RES_TABLE_TYPE_TYPE && (u8(at + 8) == typeId)) {
                    val entry = entryIndexOf(at, keyIndex)
                    if (entry != null) return (pkg.id shl 24) or (typeId shl 16) or entry
                }
                at += chunkSize
            }
        }
        return null
    }

    /** The index of the entry in this type chunk whose key is the wanted one, or null. */
    private fun entryIndexOf(chunk: Int, keyIndex: Int): Int? {
        val headerSize = u16(chunk + 2)
        val flags = u8(chunk + 9)
        val entryCount = u32(chunk + 12)
        val entriesStart = u32(chunk + 16)
        val offsets = chunk + headerSize
        val sparse = flags and FLAG_SPARSE != 0
        val short = flags and FLAG_OFFSET16 != 0
        for (i in 0 until entryCount) {
            val index: Int
            val offset: Int
            when {
                sparse -> {
                    index = u16(offsets + i * 4)
                    offset = u16(offsets + i * 4 + 2) * 4
                }
                short -> {
                    index = i
                    val raw = u16(offsets + i * 2)
                    if (raw == 0xFFFF) continue
                    offset = raw * 4
                }
                else -> {
                    index = i
                    val raw = u32(offsets + i * 4)
                    if (raw == -1) continue
                    offset = raw
                }
            }
            val entry = chunk + entriesStart + offset
            // ResTable_entry: size, flags, then the key as a string pool reference.
            if (u32(entry + 4) == keyIndex) return index
        }
        return null
    }

    private fun readStringPool(start: Int): List<String> {
        require(u16(start) == RES_STRING_POOL_TYPE) { "not a string pool at $start" }
        val headerSize = u16(start + 2)
        val count = u32(start + 8)
        val flags = u32(start + 16)
        val stringsStart = u32(start + 20)
        val utf8 = flags and UTF8_FLAG != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val offset = u32(start + headerSize + i * 4)
            out += readString(start + stringsStart + offset, utf8)
        }
        return out
    }

    private fun readString(at: Int, utf8: Boolean): String {
        var p = at
        if (utf8) {
            // Two lengths, characters then bytes, each one byte unless its high bit says two.
            if (u8(p) and 0x80 != 0) p += 2 else p += 1
            var bytes = u8(p)
            if (bytes and 0x80 != 0) {
                bytes = ((bytes and 0x7F) shl 8) or u8(p + 1)
                p += 2
            } else {
                p += 1
            }
            val data = ByteArray(bytes) { buffer.get(p + it) }
            return String(data, Charsets.UTF_8)
        }
        var chars = u16(p)
        if (chars and 0x8000 != 0) {
            chars = ((chars and 0x7FFF) shl 16) or u16(p + 2)
            p += 4
        } else {
            p += 2
        }
        val data = CharArray(chars) { buffer.getChar(p + it * 2) }
        return String(data)
    }

    private fun u8(at: Int) = buffer.get(at).toInt() and 0xFF
    private fun u16(at: Int) = buffer.getShort(at).toInt() and 0xFFFF
    private fun u32(at: Int) = buffer.getInt(at)

    companion object {
        private const val RES_STRING_POOL_TYPE = 0x0001
        private const val RES_TABLE_TYPE = 0x0002
        private const val RES_TABLE_PACKAGE_TYPE = 0x0200
        private const val RES_TABLE_TYPE_TYPE = 0x0201
        private const val UTF8_FLAG = 0x100
        private const val FLAG_SPARSE = 0x01
        private const val FLAG_OFFSET16 = 0x02

        fun parse(bytes: ByteArray): ResourceTable =
            ResourceTable(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
    }
}
