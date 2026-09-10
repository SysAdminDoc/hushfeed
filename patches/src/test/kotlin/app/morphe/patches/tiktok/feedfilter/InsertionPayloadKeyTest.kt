package app.morphe.patches.tiktok.feedfilter

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The feed key's register in the final feed insertion payload constructor.
 *
 * <p>R8 reordered that constructor's parameters between 46.2.3 and 46.7.3 without TikTok changing
 * anything: the static factory beside it still takes them the old way round. The register was
 * written into the patch as `p2`, which is the List on the newer builds.
 */
class InsertionPayloadKeyTest {
    private val payload = "LX/0SN6;"

    @Test
    fun `the key is found wherever the constructor puts it`() {
        assertEquals("p2", constructor(listOf("I", "Ljava/lang/String;", "Ljava/util/List;")).insertionPayloadKeyRegister())
        assertEquals("p3", constructor(listOf("I", "Ljava/util/List;", "Ljava/lang/String;")).insertionPayloadKeyRegister())
        assertEquals("p1", constructor(listOf("Ljava/lang/String;", "I", "Ljava/util/List;")).insertionPayloadKeyRegister())
    }

    @Test
    fun `a wide parameter ahead of the key takes two registers`() {
        // this is p0, the long is p1 and p2, the double is p3 and p4, so the key is p5.
        assertEquals("p3", constructor(listOf("J", "Ljava/lang/String;")).insertionPayloadKeyRegister())
        assertEquals("p5", constructor(listOf("J", "D", "Ljava/lang/String;")).insertionPayloadKeyRegister())
    }

    @Test
    fun `a static method has no this to count`() {
        val static = ImmutableMethod(
            payload,
            "LIZ",
            listOf("I", "Ljava/lang/String;").map { ImmutableMethodParameter(it, null, null) },
            payload,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            null,
        )
        assertEquals("p1", MutableMethod(static).insertionPayloadKeyRegister())
    }

    @Test
    fun `a constructor with no key says so`() {
        val error = assertThrows(PatchException::class.java) {
            constructor(listOf("I", "Ljava/util/List;")).insertionPayloadKeyRegister()
        }
        assertEquals(true, error.message!!.contains("takes no feed key"))
    }

    @Test
    fun `the constructor is matched by its parameters in any order`() {
        assertEquals(
            INSERTION_PAYLOAD_PARAMETERS,
            listOf("I", "Ljava/util/List;", "Ljava/lang/String;").sorted(),
        )
    }

    private fun constructor(parameters: List<String>) = MutableMethod(
        ImmutableMethod(
            payload,
            "<init>",
            parameters.map { ImmutableMethodParameter(it, null, null) },
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            null,
            null,
            null,
        ),
    )
}
