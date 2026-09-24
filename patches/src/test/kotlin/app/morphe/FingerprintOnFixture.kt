package app.morphe

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Whether the patcher would take [method] of [classDef] for this fingerprint, judged from the
 * fingerprint's own fields with Morphe patcher 1.13's rules: a type written whole (`L...;`, a
 * primitive) is compared whole, one written without its `;` by prefix, a bare `...;` by suffix;
 * parameters one for one and the same count; each string found inside some string the method
 * loads; and the custom predicate last.
 *
 * <p>Fixture tests hold the real fingerprint objects to a build with this, instead of a copy of
 * their shape, so a change to a fingerprint is what the test sees (review #7: a copied string
 * list hid the 46.x keep-list failure until a full apply). Instruction filters and class
 * fingerprints are not modelled, and a fingerprint that uses either is refused rather than half
 * judged.
 */
internal fun Fingerprint.takes(method: Method, classDef: ClassDef): Boolean {
    require(filters.isNullOrEmpty() && classFingerprint == null) {
        "instruction filters and class fingerprints are not modelled: $this"
    }
    definingClass?.let { if (!typeMatches(method.definingClass, it)) return false }
    name?.let { if (method.name != it) return false }
    accessFlags?.let { if (method.accessFlags != it) return false }
    returnType?.let { if (!typeMatches(method.returnType, it)) return false }
    parameters?.let { wanted ->
        val actual = method.parameterTypes.map(CharSequence::toString)
        if (actual.size != wanted.size || actual.zip(wanted).any { (have, want) -> !typeMatches(have, want) }) return false
    }
    strings?.let { wanted ->
        val loaded = method.implementation?.instructions
            ?.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
            .orEmpty()
        if (wanted.any { want -> loaded.none { it.contains(want) } }) return false
    }
    custom?.let { if (!it(method, classDef)) return false }
    return true
}

/** The patcher's comparison for a type as a fingerprint writes it (StringComparisonType). */
internal fun typeMatches(actual: String, wanted: String): Boolean = when {
    wanted.length == 1 -> if (wanted[0] == 'L' || wanted[0] == '[') actual.startsWith(wanted) else actual == wanted
    wanted.startsWith("[") || wanted.startsWith("L") -> if (wanted.endsWith(";")) actual == wanted else actual.startsWith(wanted)
    wanted.endsWith(";") -> actual.endsWith(wanted)
    else -> actual.contains(wanted)
}
