package io.github.albert.extractorir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class IrProtocolTest {
    private static final int DURATIONS_PER_FRAME = 24;

    @Test
    public void everyCommandContainsPreamblesAndSixDataFrames() {
        for (RemoteCommand command : RemoteCommand.values()) {
            int[] pattern = command.pattern();

            assertEquals(8 * DURATIONS_PER_FRAME, pattern.length);
            assertEquals(IrProtocol.PREAMBLE_1, decodeFrame(pattern, 0));
            assertEquals(IrProtocol.PREAMBLE_2, decodeFrame(pattern, DURATIONS_PER_FRAME));
            for (int repeat = 0; repeat < IrProtocol.DATA_REPEATS; repeat++) {
                int offset = (repeat + 2) * DURATIONS_PER_FRAME;
                assertEquals(command.bits(), decodeFrame(pattern, offset));
            }
        }
    }

    @Test
    public void patternsUseVerifiedTimingAndStayBelowAndroidLimit() {
        for (RemoteCommand command : RemoteCommand.values()) {
            int[] pattern = command.pattern();
            long totalMicroseconds = 0;
            for (int duration : pattern) {
                assertTrue(duration > 0);
                totalMicroseconds += duration;
            }

            assertEquals(0, pattern.length % 2);
            assertEquals(1_250, pattern[0]);
            assertEquals(430, pattern[1]);
            assertEquals(40_000, pattern[pattern.length - 1]);
            assertTrue(totalMicroseconds < 2_000_000);
        }
    }

    @Test
    public void rejectsMalformedCommands() {
        assertThrows(IllegalArgumentException.class, () -> IrProtocol.patternFor("101"));
        assertThrows(IllegalArgumentException.class, () -> IrProtocol.patternFor("11000000000X"));
        assertThrows(IllegalArgumentException.class, () -> IrProtocol.patternFor(null));
    }

    private static String decodeFrame(int[] pattern, int offset) {
        StringBuilder bits = new StringBuilder(12);
        for (int index = 0; index < DURATIONS_PER_FRAME; index += 2) {
            int mark = pattern[offset + index];
            bits.append(mark > 700 ? '1' : '0');
        }
        return bits.toString();
    }
}
