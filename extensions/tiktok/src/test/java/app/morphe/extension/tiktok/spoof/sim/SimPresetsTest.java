package app.morphe.extension.tiktok.spoof.sim;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Null, whitespace and validity boundaries for the bundled SIM presets. */
public class SimPresetsTest {
    @Test
    public void everyBundledPresetIsValidAndMatchesTrimmedCurrentValues() {
        for (SimPreset preset : SimPresets.PRESETS) {
            assertTrue(preset.country, preset.isValid());
            assertSame(preset, SimPresets.findSelected(
                    " " + preset.iso + " ", " " + preset.mccMnc + " ",
                    " " + preset.operatorName + " "));
        }
    }

    @Test
    public void missingCurrentValuesAreTreatedAsEmptyInsteadOfCrashing() {
        assertTrue(SimPresets.hasEmptyCurrentValues(null, null, null));
        assertFalse(SimPresets.hasEmptyCurrentValues("us", null, null));
        assertNull(SimPresets.findSelected(null, null, null));
        assertNull(SimPresets.findSelected("us", null, "T-Mobile"));
    }
}
