package me.steinborn.krypton.mod.shared.network.util;

import io.netty.buffer.ByteBuf;
import jdk.incubator.vector.*;
import me.steinborn.krypton.mod.shared.KryptonSharedInitializer;
import me.z7087.final2constant.Constant;
import me.z7087.final2constant.DynamicConstant;
import me.z7087.ial.IncubatorApiLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class VarLongUtil {
    public static final long MASK_7_BITS = -1L << 7;
    public static final long MASK_14_BITS = -1L << 14;
    public static final long MASK_21_BITS = -1L << 21;
    public static final long MASK_28_BITS = -1L << 28;
    public static final long MASK_35_BITS = -1L << 35;
    public static final long MASK_42_BITS = -1L << 42;
    public static final long MASK_49_BITS = -1L << 49;
    public static final long MASK_56_BITS = -1L << 56;
    public static final long MASK_63_BITS = -1L << 63;
    private static final byte[] VARLONG_EXACT_BYTE_LENGTHS = new byte[65];

    static {
        for (int i = 0; i < 64; ++i) {
            VARLONG_EXACT_BYTE_LENGTHS[i] = (byte) ((int) Math.ceil((64d - i) / 7d));
        }
        VARLONG_EXACT_BYTE_LENGTHS[64] = 1; // Special case for 0.
    }
    
    static final Logger LOGGER = LogManager.getLogger(VarLongUtil.class);

    public static int getVarLongLength(long value) {
        return VARLONG_EXACT_BYTE_LENGTHS[Long.numberOfLeadingZeros(value)];
    }

    public static void write(ByteBuf buf, long value) {
        KryptonSharedInitializer.getConfig().getVarLongProvider().write(buf, value);
    }

    public interface VarLongProvider {
        String getName();

        void write(ByteBuf buf, long value);

        static VarLongProvider forName(String name) {
            final VarLongProvider result;
            switch (name) {
                case MC.NAME: {
                    result = MC.getInstance();
                    break;
                }
                case VectorSIMD128.NAME: {
                    result = VectorSIMD128.getInstance();
                    break;
                }
                case BMI2.NAME: {
                    result = BMI2.getInstance();
                    break;
                }
                case SWAR.NAME: {
                    result = SWAR.getInstance();
                    break;
                }
                default: {
                    result = null;
                }
            }
            if (result != null) {
                return result;
            }
            LOGGER.warn("Unknown or non-available VarLongProvider \"{}\"", name);
            return getDefaultProvider();
        }

        static VarLongProvider getDefaultProvider() {
            return MC.getInstance();
        }
    }

    static final class MC implements VarLongProvider {
        public static final String NAME = "Minecraft";
        private static final MC INSTANCE = new MC();
        static VarLongProvider getInstance() {
            return INSTANCE;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void write(ByteBuf buf, long value) {
            while ((value & MASK_7_BITS) != 0) {
                buf.writeByte(((int) (value & 0x7FL)) | 0x80);
                value >>>= 7;
            }
            buf.writeByte((int) value);
        }
    }

    static final class VectorSIMD128 implements VarLongProvider {
        public static final String NAME = "VectorSIMD";

        @Override
        public String getName() {
            return NAME;
        }

        //? if java: >= 16 {
        private static final DynamicConstant<Boolean> DC_LOADED = Constant.factory.ofVolatile(null);
        private static final VectorSIMD128 INSTANCE = new VectorSIMD128();

        static VarLongProvider getInstance() {
            Boolean loaded = DC_LOADED.get();
            if (loaded == null) {
                final IncubatorApiLoader loader = IncubatorApiLoader.of(
                        "jdk.incubator.vector",
                        "jdk.incubator.vector.IntVector"
                );
                if (loader.isSupported() && (loader.isLoaded() || loader.tryLoad())) {
                    DC_LOADED.set(true);
                    return INSTANCE;
                }
                DC_LOADED.set(false);
                return null;
            }
            return loaded ? INSTANCE : null;
        }

        @Override
        public void write(ByteBuf buf, long value) {
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte((int) value);
            } else if ((value & MASK_14_BITS) == 0) {
                int iv = (int) value;
                buf.writeShortLE((iv & 0x7F) | ((iv & 0x3F80) << 1) | 0x80);
            } else {
                writeVarLongFull(buf, value);
            }
        }

        private static void writeVarLongFull(ByteBuf buf, long value) {
            IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, (int) value);
            int w;
            final boolean useLSHL = KryptonSharedInitializer.getConfig().isUseLSHL();
            if (useLSHL) {
                vector = vector.lanewise(VectorOperators.LSHL, VectorSIMD128Constants.LSHL_V);
            } else {
                vector = vector.lanewise(VectorOperators.MUL, VectorSIMD128Constants.MUL_V);
            }
            w = vector.reinterpretAsBytes().rearrange(VectorSIMD128Constants.SHUFFLE
                    , VectorSIMD128Constants.SHUFFLE_MASK
            ).reinterpretAsInts().lane(0);
            if ((value & MASK_21_BITS) == 0) {
                buf.writeMediumLE(w | 0x8080);
            } else if ((value & MASK_28_BITS) == 0) {
                buf.writeIntLE(w | 0x808080);
            } else {
                buf.writeIntLE(w | 0x80808080);
                vector = IntVector.broadcast(IntVector.SPECIES_128, (int) (value >>> 28));
                if (useLSHL) {
                    vector = vector.lanewise(VectorOperators.LSHL, VectorSIMD128Constants.LSHL_V);
                } else {
                    vector = vector.lanewise(VectorOperators.MUL, VectorSIMD128Constants.MUL_V);
                }
                w = vector.reinterpretAsBytes().rearrange(VectorSIMD128Constants.SHUFFLE
                        , VectorSIMD128Constants.SHUFFLE_MASK
                ).reinterpretAsInts().lane(0);
                if ((value & MASK_35_BITS) == 0) {
                    buf.writeByte(w);
                } else if ((value & MASK_42_BITS) == 0) {
                    buf.writeShortLE(w | 0x80);
                } else if ((value & MASK_49_BITS) == 0) {
                    buf.writeMediumLE(w | 0x8080);
                } else if ((value & MASK_56_BITS) == 0) {
                    buf.writeIntLE(w | 0x808080);
                } else {
                    buf.writeIntLE(w | 0x80808080);
                    int iv = (int) (value >>> 56);
                    if ((value & MASK_63_BITS) == 0) {
                        buf.writeByte(iv);
                    } else {
                        buf.writeShortLE(iv | 0x100);
                    }
                }
            }
        }

        static final class VectorSIMD128Constants {
            private VectorSIMD128Constants() {}
            static final IntVector LSHL_V = fromArray(0, 1, 2, 3);
            static final IntVector MUL_V = fromArray(1, 2, 4, 8);
            private static IntVector fromArray(int... arr) {
                return IntVector.fromArray(IntVector.SPECIES_128, arr, 0);
            }
            static final VectorShuffle<Byte> SHUFFLE = VectorShuffle.fromValues(
                    ByteVector.SPECIES_128,
                    0, 5, 10, 15,
                    4, 5, 6, 7,
                    8, 9, 10, 11,
                    12, 13, 14, 15
            );
            static final VectorMask<Byte> SHUFFLE_MASK = VectorMask.fromValues(
                    ByteVector.SPECIES_128,
                    true, true, true, true,
                    false, false, false, false,
                    false, false, false, false,
                    false, false, false, false
            );
        }
        //? } else {
        /*static VarIntProvider getInstance() {
            return null;
        }
        @Override
        public void write(ByteBuf buf, int value) {
            throw new UnsupportedOperationException();
        }
        *///? }
    }

    // i failed to find a way to do simd with 256bits

    static final class BMI2 implements VarLongProvider {
        public static final String NAME = "BMI2";
        public static final long MASK = 0x7F7F7F7F7F7F7F7FL;
        private static final MethodHandle MH_LONG_EXPAND;
        static {
            MethodHandle mhLongExpand;
            try {
                mhLongExpand = MethodHandles.lookup().findStatic(Long.class, "expand", MethodType.methodType(long.class, long.class, long.class));
            } catch (NoSuchMethodException ignored) {
                mhLongExpand = null;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            MH_LONG_EXPAND = mhLongExpand;
        }
        private static final BMI2 INSTANCE = new BMI2();
        static VarLongProvider getInstance() {
            return (MH_LONG_EXPAND != null) ? INSTANCE : null;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void write(ByteBuf buf, long value) {
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte((int) value);
            } else if ((value & MASK_14_BITS) == 0) {
                int iv = (int) value;
                buf.writeShortLE((iv & 0x7F) | ((iv & 0x3F80) << 1) | 0x80);
            } else {
                writeVarLongFull(buf, value);
            }
        }

        private static void writeVarLongFull(ByteBuf buf, long value) {
            long w;
            //? if java: >=19 {
            w = Long.expand(value, MASK);
            //?} else {
            /*try {
                w = (long) MH_LONG_EXPAND.invokeExact(value, MASK);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            *///? }
            if ((value & MASK_21_BITS) == 0) {
                buf.writeMediumLE(((int) w) | 0x8080);
            } else if ((value & MASK_28_BITS) == 0) {
                buf.writeIntLE(((int) w) | 0x808080);
            } else {
                buf.writeIntLE(((int) w) | 0x80808080);
                w >>>= 32;
                if ((value & MASK_35_BITS) == 0) {
                    buf.writeByte((int) w);
                } else if ((value & MASK_42_BITS) == 0) {
                    buf.writeShortLE(((int) w) | 0x80);
                } else if ((value & MASK_49_BITS) == 0) {
                    buf.writeMediumLE(((int) w) | 0x8080);
                } else if ((value & MASK_56_BITS) == 0) {
                    buf.writeIntLE(((int) w) | 0x808080);
                } else {
                    buf.writeIntLE(((int) w) | 0x80808080);
                    int iv = (int) (value >>> 56);
                    if ((value & MASK_63_BITS) == 0) {
                        buf.writeByte(iv);
                    } else {
                        buf.writeShortLE(iv | 0x100);
                    }
                }
            }
        }
    }

    static final class SWAR implements VarLongProvider {
        public static final String NAME = "SWAR";
        private static final SWAR INSTANCE = new SWAR();
        static VarLongProvider getInstance() {
            return INSTANCE;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void write(ByteBuf buf, long value) {
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte((int) value);
            } else if ((value & MASK_14_BITS) == 0) {
                int iv = (int) value;
                buf.writeShortLE((iv & 0x7F) | ((iv & 0x3F80) << 1) | 0x80);
            } else {
                writeVarLongFull(buf, value);
            }
        }

        private static void writeVarLongFull(ByteBuf buf, long value) {
            long w = expand56to64(value);
            if ((value & MASK_21_BITS) == 0) {
                buf.writeMediumLE(((int) w) | 0x8080);
            } else if ((value & MASK_28_BITS) == 0) {
                buf.writeIntLE(((int) w) | 0x808080);
            } else {
                buf.writeIntLE(((int) w) | 0x80808080);
                w >>>= 32;
                if ((value & MASK_35_BITS) == 0) {
                    buf.writeByte((int) w);
                } else if ((value & MASK_42_BITS) == 0) {
                    buf.writeShortLE(((int) w) | 0x80);
                } else if ((value & MASK_49_BITS) == 0) {
                    buf.writeMediumLE(((int) w) | 0x8080);
                } else if ((value & MASK_56_BITS) == 0) {
                    buf.writeIntLE(((int) w) | 0x808080);
                } else {
                    buf.writeIntLE(((int) w) | 0x80808080);
                    int iv = (int) (value >>> 56);
                    if ((value & MASK_63_BITS) == 0) {
                        buf.writeByte(iv);
                    } else {
                        buf.writeShortLE(iv | 0x100);
                    }
                }
            }
        }

        private static long expand56to64(long value) {

            value = (value & 0x000000000FFFFFFFL) | ((value & 0x00FFFFFFF0000000L) << 4);

            value = (value & 0x00003FFF00003FFFL) | ((value & 0x0FFFC0000FFFC000L) << 2);

            value = (value & 0x007F007F007F007FL) | ((value & 0x3F803F803F803F80L) << 1);

            return value;
        }
    }
}
