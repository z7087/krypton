package me.steinborn.krypton.mod.shared.network.util;

import io.netty.buffer.ByteBuf;
import jdk.incubator.vector.*;
import me.steinborn.krypton.mod.shared.KryptonSharedInitializer;
import me.z7087.final2constant.Constant;
import me.z7087.final2constant.DynamicConstant;
import me.z7087.ial.IncubatorApiLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Maps VarInt byte sizes to a lookup table corresponding to the number of bits in the integer,
 * from zero to 32.
 */
public class VarIntUtil {
    private static final int MASK_7_BITS = -1 << 7;
    private static final int MASK_14_BITS = -1 << 14;
    private static final int MASK_21_BITS = -1 << 21;
    private static final int MASK_28_BITS = -1 << 28;
    private static final byte[] VARINT_EXACT_BYTE_LENGTHS = new byte[33];

    static {
        for (int i = 0; i < 32; ++i) {
            VARINT_EXACT_BYTE_LENGTHS[i] = (byte) ((int) Math.ceil((32d - i) / 7d));
        }
        VARINT_EXACT_BYTE_LENGTHS[32] = 1; // Special case for 0.
    }

    public static int getVarIntLength(int value) {
        return VARINT_EXACT_BYTE_LENGTHS[Integer.numberOfLeadingZeros(value)];
    }

    private static final DynamicConstant<VarIntProvider> DC_PROVIDER = Constant.factory.ofVolatile(null);

    public static void write(ByteBuf buf, int value) {
        DC_PROVIDER.orElseThrow().write(buf, value);
    }

    public static void setProvider(VarIntProvider provider) {
        DC_PROVIDER.set(provider);
    }

    public interface VarIntProvider {
        void write(ByteBuf buf, int value);
    }

    static final class MC implements VarIntProvider {
        private static final MC INSTANCE = new MC();
        static VarIntProvider getInstance() {
            return INSTANCE;
        }
        @Override
        public void write(ByteBuf buf, int value) {
            while ((value & MASK_7_BITS) != 0) {
                buf.writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buf.writeByte(value);
        }
    }

    static final class Astei implements VarIntProvider {
        private static final Astei INSTANCE = new Astei();
        static VarIntProvider getInstance() {
            return INSTANCE;
        }
        @Override
        public void write(ByteBuf buf, int value) {
            // Peel the one and two byte count cases explicitly as they are the most common VarInt sizes
            // that the server will send, to improve inlining.
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte(value);
            } else if ((value & MASK_14_BITS) == 0) {
                int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
                buf.writeShort(w);
            } else {
                writeVarIntFull(buf, value);
            }
        }

        private static void writeVarIntFull(ByteBuf buf, int value) {
            // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte(value);
            } else if ((value & MASK_14_BITS) == 0) {
                int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
                buf.writeShort(w);
            } else if ((value & MASK_21_BITS) == 0) {
                int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
                buf.writeMedium(w);
            } else if ((value & MASK_28_BITS) == 0) {
                int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
                        | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
                buf.writeInt(w);
            } else {
                int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
                        | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
                buf.writeInt(w);
                buf.writeByte(value >>> 28);
            }
        }
    }

    static final class VectorSIMD implements VarIntProvider {
        private static final DynamicConstant<Boolean> DC_LOADED = Constant.factory.ofVolatile(null);
        private static final VectorSIMD INSTANCE = new VectorSIMD();
        static VarIntProvider getInstance() {
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
        public void write(ByteBuf buf, int value) {
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte(value);
            } else if ((value & MASK_14_BITS) == 0) {
                buf.writeShortLE((value & 0x7F) | ((value & 0x3F80) << 1) | 0x80);
            } else {
                writeVarIntFull(buf, value);
            }
        }

        private static void writeVarIntFull(ByteBuf buf, int value) {
            IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
            int w;
            if (KryptonSharedInitializer.getConfig().isUseLSHL()) {
                vector = vector.lanewise(VectorOperators.LSHL, VectorSIMDConstants.LSHL_V);
            } else {
                vector = vector.lanewise(VectorOperators.MUL, VectorSIMDConstants.MUL_V);
            }
            w = vector.reinterpretAsBytes().rearrange(VectorSIMDConstants.SHUFFLE
                    , VectorSIMDConstants.SHUFFLE_MASK // why the mask speeds up the shuffle???
            ).reinterpretAsInts().lane(0);
            if ((value & MASK_21_BITS) == 0) {
                buf.writeMediumLE(w | 0x808000);
            } else if ((value & MASK_28_BITS) == 0) {
                buf.writeIntLE(w | 0x80808000);
            } else {
                buf.writeIntLE(w | 0x80808080);
                buf.writeByte(value >>> 28);
            }
        }

        static final class VectorSIMDConstants {
            private VectorSIMDConstants() {}
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
    }
    
    static final class BMI2 implements VarIntProvider {
        public static final int MASK = 0x7F7F7F7F;
        private static final MethodHandle MH_INTEGER_EXPAND;
        static {
            MethodHandle mhIntegerExpand;
            try {
                mhIntegerExpand = MethodHandles.lookup().findStatic(Integer.class, "expand", MethodType.methodType(int.class, int.class, int.class));
            } catch (NoSuchMethodException ignored) {
                mhIntegerExpand = null;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            MH_INTEGER_EXPAND = mhIntegerExpand;
        }
        private static final BMI2 INSTANCE = new BMI2();
        static VarIntProvider getInstance() {
            return (MH_INTEGER_EXPAND != null) ? INSTANCE : null;
        }

        @Override
        public void write(ByteBuf buf, int value) {
            if ((value & MASK_7_BITS) == 0) {
                buf.writeByte(value);
            } else if ((value & MASK_14_BITS) == 0) {
                buf.writeShortLE((value & 0x7F) | ((value & 0x3F80) << 1) | 0x80);
            } else {
                writeVarIntFull(buf, value);
            }
        }

        private static void writeVarIntFull(ByteBuf buf, int value) {
            int w;
            //? if java: >=19 {
            w = Integer.expand(value, MASK);
            //?} else {
            /*try {
                w = (int) MH_INTEGER_EXPAND.invokeExact(value, MASK);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            *///? }
            if ((value & MASK_21_BITS) == 0) {
                buf.writeMediumLE(w | 0x8080);
            } else if ((value & MASK_28_BITS) == 0) {
                buf.writeIntLE(w | 0x808080);
            } else {
                buf.writeIntLE(w | 0x80808080);
                buf.writeByte(value >>> 28);
            }
        }
    }
}
