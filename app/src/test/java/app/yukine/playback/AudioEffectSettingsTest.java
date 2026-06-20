package app.yukine.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioEffectSettingsTest {
    @Test
    public void encodesAndDecodesSettings() {
        AudioEffectSettings settings = new AudioEffectSettings(
                true,
                AudioEffectSettings.PRESET_CUSTOM,
                new short[]{100, -200},
                (short) 500,
                (short) 1000,
                600
        );

        AudioEffectSettings decoded = AudioEffectSettings.decode(settings.encode());

        assertTrue(decoded.enabled);
        assertEquals(AudioEffectSettings.PRESET_CUSTOM, decoded.preset);
        assertEquals(2, decoded.bandLevels.length);
        assertEquals(100, decoded.bandLevels[0]);
        assertEquals(-200, decoded.bandLevels[1]);
        assertEquals(500, decoded.bassBoostStrength);
        assertEquals(1000, decoded.virtualizerStrength);
        assertEquals(600, decoded.loudnessGainMb);
    }

    @Test
    public void malformedSettingsFallBackSafely() {
        AudioEffectSettings decoded = AudioEffectSettings.decode("enabled=false;preset=nope;bass=9999;loudness=-9999");

        assertFalse(decoded.enabled);
        assertEquals(AudioEffectSettings.PRESET_CUSTOM, decoded.preset);
        assertEquals(1000, decoded.bassBoostStrength);
        assertEquals(-1200, decoded.loudnessGainMb);
    }
}
