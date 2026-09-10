package app.morphe.patches.tiktok.misc.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
 * covers the layouts the reader has to understand: both string encodings including the two-unit
 * length forms, the two compact entry-offset forms newer aapt2 writes, compact entries, a package
 * whose type ids are offset, which package of several answers, and the ways an entry can be
 * missing.
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
        assertEquals(0x7f010001, table.idOf("raw", "icon_2pt_settings_stroke", APP))
        assertEquals(0x7f010000, table.idOf("raw", "alpha", APP))
        assertEquals(0x7f010002, table.idOf("raw", "zeta", APP))
        // The same key in another type is another resource with another id.
        assertEquals(0x7f020000, table.idOf("drawable", "alpha", APP))
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
        assertNull(table.idOf("raw", "beta", APP))
        assertNull(table.idOf("drawable", "beta", APP))
        assertNull(table.idOf("raw", "gamma", APP))
        assertNull(table.idOf("string", "alpha", APP))
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
                assertEquals(label, 0x7f010000, table.idOf("raw", "first", APP))
                assertEquals(label, 0x7f010002, table.idOf("raw", "third", APP))
                assertNull(label, table.idOf("raw", "second", APP))
            }
        }
    }

    @Test
    fun `a library package carrying the same name does not answer for the app`() {
        // TikTok's table has twelve packages with the app's own last, and three names in it are
        // in a library package as well. Reading in file order answered from the library.
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x4f,
                    name = "com.example.library",
                    types = listOf("raw"),
                    keys = listOf("icon_2pt_settings_stroke"),
                    chunks = listOf(type(1, listOf(0))),
                ),
                pkg(
                    id = 0x7f,
                    types = listOf("raw"),
                    keys = listOf("other", "icon_2pt_settings_stroke"),
                    chunks = listOf(type(1, listOf(0, 1))),
                ),
            ),
        )
        assertEquals(0x7f010001, table.idOf("raw", "icon_2pt_settings_stroke", APP))
    }

    @Test
    fun `a table that declares the app under another name still answers from its own package`() {
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x4f,
                    name = "com.example.library",
                    types = listOf("raw"),
                    keys = listOf("icon"),
                    chunks = listOf(type(1, listOf(0))),
                ),
                pkg(
                    id = 0x7f,
                    name = "com.example.app.before.the.rename",
                    types = listOf("raw"),
                    keys = listOf("icon"),
                    chunks = listOf(type(1, listOf(0))),
                ),
            ),
        )
        assertEquals(0x7f010000, table.idOf("raw", "icon", APP))
    }

    @Test
    fun `a table with no app package says so rather than picking one`() {
        val table = ResourceTable.parse(
            table(
                pkg(id = 0x4f, name = "one", types = listOf("raw"), keys = listOf("icon"), chunks = listOf(type(1, listOf(0)))),
                pkg(id = 0x53, name = "two", types = listOf("raw"), keys = listOf("icon"), chunks = listOf(type(1, listOf(0)))),
            ),
        )
        val error = assertThrows(IllegalStateException::class.java) { table.idOf("raw", "icon", APP) }
        assertTrue(error.message, error.message!!.contains("one=0x4f"))
        assertTrue(error.message, error.message!!.contains("two=0x53"))
    }

    @Test
    fun `the flags a real table sets on a plain entry are not the compact bit`() {
        // Every entry in TikTok's own package carries flags 0x0, 0x1 or 0x4. A reader masking
        // the wrong bit would take those for compact entries and read their values as keys, and
        // a suite whose entries all carry zero would not notice.
        for (entryFlags in listOf(0x1, 0x4, 0x5)) {
            val table = ResourceTable.parse(
                table(
                    pkg(
                        id = 0x7f,
                        types = listOf("raw"),
                        keys = listOf("first", "second"),
                        chunks = listOf(type(1, listOf(0, 1), entryFlags = entryFlags)),
                    ),
                ),
            )
            assertEquals("flags=$entryFlags", 0x7f010000, table.idOf("raw", "first", APP))
            assertEquals("flags=$entryFlags", 0x7f010001, table.idOf("raw", "second", APP))
        }
    }

    @Test
    fun `a type id offset moves every id in the package`() {
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x7f,
                    types = listOf("raw", "drawable"),
                    keys = listOf("icon"),
                    chunks = listOf(type(id = 8, entries = listOf(0)), type(id = 9, entries = listOf(0))),
                    typeIdOffset = 7,
                ),
            ),
        )
        assertEquals(0x7f080000, table.idOf("raw", "icon", APP))
        assertEquals(0x7f090000, table.idOf("drawable", "icon", APP))
    }

    @Test
    fun `names past the one byte length form read whole in both encodings`() {
        // A pool writes its lengths in one byte until the high bit says two follow, and the two
        // encodings put the extra byte in different places.
        val long8 = "a".repeat(0x90)
        val long16 = "b".repeat(0x9000)
        for (utf8 in listOf(true, false)) {
            val name = if (utf8) long8 else long16
            val table = ResourceTable.parse(
                table(
                    pkg(
                        id = 0x7f,
                        types = listOf("raw"),
                        keys = listOf("short", name),
                        chunks = listOf(type(1, listOf(0, 1))),
                        utf8 = utf8,
                    ),
                ),
            )
            assertEquals("utf8=$utf8", 0x7f010001, table.idOf("raw", name, APP))
            assertEquals("utf8=$utf8", 0x7f010000, table.idOf("raw", "short", APP))
        }
    }

    @Test
    fun `a compact entry keeps its key where the size would be`() {
        val table = ResourceTable.parse(
            table(
                pkg(
                    id = 0x7f,
                    types = listOf("raw"),
                    // The value written into a compact entry is the key index of the other entry,
                    // which is what a reader looking at the wrong offset would match on.
                    keys = listOf("first", "second"),
                    chunks = listOf(type(1, listOf(0, 1), compact = true)),
                ),
            ),
        )
        assertEquals(0x7f010000, table.idOf("raw", "first", APP))
        assertEquals(0x7f010001, table.idOf("raw", "second", APP))
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
        assertEquals(0x7f010000, table.idOf("raw", "only", APP))
    }

    // ---- a small encoder, the mirror of what the reader decodes ----

    private companion object {
        const val FLAG_SPARSE = 0x01
        const val FLAG_OFFSET16 = 0x02
        const val FLAG_COMPACT = 0x0008
        const val APP = "com.example.app"
    }

    private class Chunk(val bytes: ByteArray)

    private fun le(): ByteArrayOutputStream = ByteArrayOutputStream()
    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xFF)
    private fun ByteArrayOutputStream.u16(v: Int) { u8(v); u8(v ushr 8) }
    private fun ByteArrayOutputStream.u32(v: Int) { u16(v and 0xFFFF); u16(v ushr 16) }

    /** A pool length: one unit, or two with the high bit set on the first and the high half in it. */
    private fun ByteArrayOutputStream.len8(v: Int) {
        if (v < 0x80) u8(v) else { u8((v ushr 8) or 0x80); u8(v and 0xFF) }
    }

    private fun ByteArrayOutputStream.len16(v: Int) {
        if (v < 0x8000) u16(v) else { u16((v ushr 16) or 0x8000); u16(v and 0xFFFF) }
    }

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
                data.len8(s.length)
                data.len8(bytes.size)
                data.write(bytes)
                data.u8(0)
            } else {
                data.len16(s.length)
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
    private fun type(
        id: Int,
        entries: List<Int?>,
        flags: Int = 0,
        compact: Boolean = false,
        entryFlags: Int = 0,
    ): Chunk {
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
            if (compact) {
                // The key takes the size field, the flag says so, and the value sits where a
                // plain entry keeps its key. The value here is another entry's key index, so a
                // reader that looks in the wrong place matches the wrong entry rather than none.
                body.u16(k!!)
                body.u16(FLAG_COMPACT)
                body.u32(entries.size - 1 - k)
            } else {
                body.u16(entrySize)
                body.u16(entryFlags)
                body.u32(k!!)
            }
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

    private fun pkg(
        id: Int,
        types: List<String>,
        keys: List<String>,
        chunks: List<Chunk>,
        utf8: Boolean = true,
        name: String = APP,
        typeIdOffset: Int = 0,
    ): Chunk {
        val headerSize = 288
        val typePool = stringPool(types, utf8)
        val keyPool = stringPool(keys, utf8)
        val header = le()
        header.u32(id)
        val declared = name.toCharArray()
        for (i in 0 until 128) header.u16(if (i < declared.size) declared[i].code else 0)
        header.u32(headerSize)                 // typeStrings offset
        header.u32(types.size)                 // lastPublicType
        header.u32(headerSize + typePool.size) // keyStrings offset
        header.u32(keys.size)                  // lastPublicKey
        header.u32(typeIdOffset)
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
