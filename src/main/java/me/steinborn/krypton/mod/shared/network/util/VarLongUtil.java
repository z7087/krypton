package me.steinborn.krypton.mod.shared.network.util;

public class VarLongUtil {
    private static final byte[] VARLONG_EXACT_BYTE_LENGTHS = new byte[65];

    static {
        for (int i = 0; i < 64; ++i) {
            VARLONG_EXACT_BYTE_LENGTHS[i] = (byte) ((int) Math.ceil((64d - i) / 7d));
        }
        VARLONG_EXACT_BYTE_LENGTHS[64] = 1; // Special case for 0.
    }

    public static int getVarLongLength(long value) {
        return VARLONG_EXACT_BYTE_LENGTHS[Long.numberOfLeadingZeros(value)];
    }
}
