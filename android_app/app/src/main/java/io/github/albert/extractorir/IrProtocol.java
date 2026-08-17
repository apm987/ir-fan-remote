package io.github.albert.extractorir;

/**
 * Generates the measured F12/BA5104-compatible mark/space stream.
 *
 * <p>Every returned array starts with an IR mark and then alternates mark and
 * space durations, in microseconds, as required by Android's ConsumerIrManager.
 */
public final class IrProtocol {
    public static final int CARRIER_FREQUENCY_HZ = 38_000;
    public static final int DATA_REPEATS = 6;

    static final String PREAMBLE_1 = "110000000000";
    static final String PREAMBLE_2 = "110001111111";

    private static final int MARK_SHORT_US = 410;
    private static final int MARK_LONG_US = 1_250;
    private static final int SPACE_SHORT_US = 430;
    private static final int SPACE_LONG_US = 1_270;
    private static final int LAST_SPACE_ZERO_US = 8_000;
    private static final int LAST_SPACE_ONE_US = 7_160;
    private static final int TRAILING_GAP_US = 40_000;

    private static final int BITS_PER_FRAME = 12;
    private static final int DURATIONS_PER_FRAME = BITS_PER_FRAME * 2;
    private static final int FRAME_COUNT = 2 + DATA_REPEATS;

    private IrProtocol() {
    }

    public static int[] patternFor(String commandBits) {
        validateBits(commandBits);

        int[] pattern = new int[FRAME_COUNT * DURATIONS_PER_FRAME];
        int offset = 0;
        offset = appendFrame(pattern, offset, PREAMBLE_1, false);
        offset = appendFrame(pattern, offset, PREAMBLE_2, false);

        for (int repeat = 0; repeat < DATA_REPEATS; repeat++) {
            boolean isLastFrame = repeat == DATA_REPEATS - 1;
            offset = appendFrame(pattern, offset, commandBits, isLastFrame);
        }

        if (offset != pattern.length) {
            throw new IllegalStateException("Longitud de patron IR inesperada");
        }
        return pattern;
    }

    private static int appendFrame(
            int[] target,
            int offset,
            String bits,
            boolean isLastFrame
    ) {
        for (int bitIndex = 0; bitIndex < BITS_PER_FRAME; bitIndex++) {
            boolean one = bits.charAt(bitIndex) == '1';
            target[offset++] = one ? MARK_LONG_US : MARK_SHORT_US;

            if (bitIndex < BITS_PER_FRAME - 1) {
                target[offset++] = one ? SPACE_SHORT_US : SPACE_LONG_US;
            } else if (isLastFrame) {
                target[offset++] = TRAILING_GAP_US;
            } else {
                target[offset++] = one ? LAST_SPACE_ONE_US : LAST_SPACE_ZERO_US;
            }
        }
        return offset;
    }

    private static void validateBits(String bits) {
        if (bits == null || bits.length() != BITS_PER_FRAME) {
            throw new IllegalArgumentException("El comando debe contener 12 bits");
        }
        for (int index = 0; index < bits.length(); index++) {
            char bit = bits.charAt(index);
            if (bit != '0' && bit != '1') {
                throw new IllegalArgumentException("El comando solo puede contener 0 y 1");
            }
        }
    }
}
