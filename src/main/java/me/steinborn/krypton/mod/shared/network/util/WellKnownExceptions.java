package me.steinborn.krypton.mod.shared.network.util;

public enum WellKnownExceptions {
    ;

    //public static final QuietDecoderException BAD_LENGTH_CACHED = new QuietDecoderException("Bad packet length");
    public static final QuietDecoderException DECODING_VARINT21_BIG_CACHED = new QuietDecoderException("Decoding VarInt21 too big");
    public static final QuietDecoderException ENCODING_VARINT21_BIG_CACHED = new QuietDecoderException("Encoding VarInt21 too big");
    public static final QuietDecoderException DECODING_FRAME_EMPTY_CACHED = new QuietDecoderException("Decoding frame length is 0");
}
