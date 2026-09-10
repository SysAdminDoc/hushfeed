package app.morphe.patches.tiktok.misc.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The resource table reader, against tables written here so that every answer is known.
 *
 * <p>The real table was checked once by hand: `raw/icon_2pt_settings_stroke` reads as
 * `0x7f010088` from the 46.2.3, 46.7.3 and 46.8.3 tables, which is what aapt2 reports for each.
 * That check is not in the suite because the tables are not in the repository. What is here
 * covers the layouts the reader has to understand: both string encodings, the two compact
 * entry-offset forms newer aapt2 writes, a table with more than one package, and the ways an
 * entry can be missing.
 */
class ResourceTableTest {

    @Test
    fun `an entry is found by type and name and its id is assembled from the three parts`() {
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x7f,
                    types = listOf("raw", "drawable"),
                    keys = listOf("alpha", "icon_2pt_settings_stroke", "zeta"),
                    chunks = listOf(
                        type(id = 1, entries = listOf(0, 1, 2)),
                        type(id = 2, entries = listOf(0)),
                    ),
                ),
            ),
        )
        assertEquals(0x7f010001, table.idOf("raw", "icon_2pt_settings_stroke"))
        assertEquals(0x7f010000, table.idOf("raw", "alpha"))
        assertEquals(0x7f010002, table.idOf("raw", "zeta"))
        // The same key in another type is another resource with another id.
        assertEquals(0x7f020000, table.idOf("drawable", "alpha"))
    }

    @Test
    fun `a name that is not there answers null, in every way it can be not there`() {
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x7f,
                    types = listOf("raw", "drawable"),
                    keys = listOf("alpha", "beta"),
                    // beta is in the key pool but no raw entry uses it, and drawable has a
                    // slot for it that is marked absent.
                    chunks = listOf(
                        type(id = 1, entries = listOf(0)),
                        type(id = 2, entries = listOf(0, null)),
                    ),
                ),
            ),
        )
        assertNull(table.idOf("raw", "beta"))
        assertNull(table.idOf("drawable", "beta"))
        assertNull(table.idOf("raw", "gamma"))
        assertNull(table.idOf("string", "alpha"))
    }

    @Test
    fun `utf16 pools and both compact offset forms read the same as the plain layout`() {
        for (utf8 in listOf(true, false)) {
            for (flags in listOf(0, FLAG_OFFSET16, FLAG_SPARSE)) {
                val table = ResourceTable.parse(
                    table(
                        pkg(
                            id = 0x7f,
                            types = listOf("raw"),
                            keys = listOf("first", "second", "third"),
                            chunks = listOf(type(id = 1, entries = listOf(0, null, 2), flags = flags)),
                            utf8 = utf8,
                        ),
                    ),
                )
                val label = "utf8=$utf8 flags=$flags"
                assertEquals(label, 0x7f010000, table.idOf("raw", "first"))
                assertEquals(label, 0x7f010002, table.idOf("raw", "third"))
                assertNull(label, table.idOf("raw", "second"))
            }
        }
    }

    @Test
    fun `a second package is searched too and keeps its own id`() {
        val table = ResourceTable.parse(
            table(
                pkg(id = 0x01, types = listOf("attr"), keys = listOf("x"), chunks = listOf(type(1, listOf(0)))),
                pkg(id = 0x7f, types = listOf("raw"), keys = listOf("y"), chunks = listOf(type(1, listOf(0)))),
            ),
        )
        assertEquals(0x01010000, table.idOf("attr", "x"))
        assertEquals(0x7f010000, table.idOf("raw", "y"))
    }

    @Test
    fun `a chunk that is not a type is stepped over by its declared size`() {
        // A type spec sits ahead of every type chunk in a real table, and anything the reader
        // does not know has to be skipped rather than read as entries.
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x7f,
                    types = listOf("raw"),
                    keys = listOf("only"),
                    chunks = listOf(typeSpec(1, 1), unknownChunk(), type(1, listOf(0))),
                ),
            ),
        )
        assertEquals(0x7f010000, table.idOf("raw", "only"))
    }

    // ---- a small encoder, the mirror of what the reader decodes ----

    private companion object {
        const val FLAG_SPARSE = 0x01
        const val FLAG_OFFSET16 = 0x02
    }

    private class Chunk(val bytes: ByteArray)

    private fun le(): ByteArrayOutputStream = ByteArrayOutputStream()
    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xFF)
    private fun ByteArrayOutputStream.u16(v: Int) { u8(v); u8(v ushr 8) }
    private fun ByteArrayOutputStream.u32(v: Int) { u16(v and 0xFFFF); u16(v ushr 16) }

    private fun chunk(type: Int, headerSize: Int, header: ByteArray, body: ByteArray): ByteArray {
        val out = le()
        out.u16(type)
        out.u16(headerSize)
        out.u32(8 + header.size + body.size)
        out.write(header)
        out.write(body)
        return out.toByteArray()
    }

    private fun stringPool(strings: List<String>, utf8: Boolean): ByteArray {
        val data = le()
        val offsets = mutableListOf<Int>()
        for (s in strings) {
            offsets += data.size()
            if (utf8) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                data.u8(s.length)
                data.u8(bytes.size)
                data.write(bytes)
                data.u8(0)
            } else {
                data.u16(s.length)
                for (c in s) data.u16(c.code)
                data.u16(0)
            }
        }
        val headerSize = 28
        val stringsStart = headerSize + strings.size * 4
        val header = le()
        header.u32(strings.size)
        header.u32(0)
        header.u32(if (utf8) 0x100 else 0)
        header.u32(stringsStart)
        header.u32(0)
        val body = le()
        for (o in offsets) body.u32(o)
        body.write(data.toByteArray())
        return chunk(0x0001, headerSize, header.toByteArray(), body.toByteArray())
    }

    /** A type chunk for one type id, `entries[i]` being the key index of entry i or null. */
    private fun type(id: Int, entries: List<Int?>, flags: Int = 0): Chunk {
        val headerSize = 20 + 56 // ResTable_type header plus a minimal ResTable_config of 56 bytes
        val present = entries.withIndex().filter { it.value != null }
        val entrySize = 8
        val offsetsSize = when {
            flags and FLAG_SPARSE != 0 -> present.size * 4
            flags and FLAG_OFFSET16 != 0 -> entries.size * 2
            else -> entries.size * 4
        }
        val entriesStart = headerSize + offsetsSize
        val header = le()
        header.u8(id)
        header.u8(flags)
        header.u16(0)
        header.u32(if (flags and FLAG_SPARSE != 0) present.size else entries.size)
        header.u32(entriesStart)
        header.u32(56) // config size
        repeat(52) { header.u8(0) }
        val body = le()
        val entryOffsets = HashMap<Int, Int>()
        var next = 0
        for ((i, k) in entries.withIndex()) if (k != null) { entryOffsets[i] = next; next += entrySize }
        when {
            flags and FLAG_SPARSE != 0 -> for ((i, _) in present) { body.u16(i); body.u16(entryOffsets.getValue(i) / 4) }
            flags and FLAG_OFFSET16 != 0 -> for (i in entries.indices) body.u16(entryOffsets[i]?.let { it / 4 } ?: 0xFFFF)
            else -> for (i in entries.indices) body.u32(entryOffsets[i] ?: -1)
        }
        for ((_, k) in present) {
            body.u16(entrySize)
            body.u16(0)
            body.u32(k!!)
        }
        return Chunk(chunk(0x0201, headerSize, header.toByteArray(), body.toByteArray()))
    }

    private fun typeSpec(id: Int, entryCount: Int): Chunk {
        val header = le()
        header.u8(id); header.u8(0); header.u16(0); header.u32(entryCount)
        val body = le()
        repeat(entryCount) { body.u32(0) }
        return Chunk(chunk(0x0202, 16, header.toByteArray(), body.toByteArray()))
    }

    private fun unknownChunk(): Chunk = Chunk(chunk(0x0203, 12, ByteArray(4), ByteArray(24)))

    private fun pkg(id: Int, types: List<String>, keys: List<String>, chunks: List<Chunk>, utf8: Boolean = true): Chunk {
        val headerSize = 288
        val typePool = stringPool(types, utf8)
        val keyPool = stringPool(keys, utf8)
        val header = le()
        header.u32(id)
        val name = "test".toCharArray()
        for (i in 0 until 128) header.u16(if (i < name.size) name[i].code else 0)
        header.u32(headerSize)                 // typeStrings offset
        header.u32(types.size)                 // lastPublicType
        header.u32(headerSize + typePool.size) // keyStrings offset
        header.u32(keys.size)                  // lastPublicKey
        header.u32(0)                          // typeIdOffset
        val body = le()
        body.write(typePool)
        body.write(keyPool)
        for (c in chunks) body.write(c.bytes)
        return Chunk(chunk(0x0200, headerSize, header.toByteArray(), body.toByteArray()))
    }

    private fun table(vararg packages: Chunk): ByteArray {
        val header = le()
        header.u32(packages.size)
        val body = le()
        body.write(stringPool(emptyList(), true))
        for (p in packages) body.write(p.bytes)
        return chunk(0x0002, 12, header.toByteArray(), body.toByteArray())
    }
}
