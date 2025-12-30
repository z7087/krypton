package me.steinborn.krypton.mixin.shared.network.microopt;


import io.netty.buffer.ByteBuf;
import me.steinborn.krypton.mod.shared.network.util.VarLongUtil;
import net.minecraft.network.VarLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VarLong.class)
public class VarLongsMixin {
    /**
     * @author z7087
     * @reason optimized version
     */
    @Overwrite
    public static int getByteSize(long v) {
        return VarLongUtil.getVarLongLength(v);
    }

    /**
     * @author z7087
     * @reason optimized version
     */
    @Overwrite
    public static ByteBuf write(ByteBuf buf, long value) {
        // Peel the one and two byte count cases explicitly as they are the most common VarInt sizes
        // that the server will send, to improve inlining.
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte((int) value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (int) ((value & 0x7FL | 0x80L) << 8 | (value >>> 7));
            buf.writeShort(w);
        } else {
            writeVarLongFull(buf, value);
        }

        return buf;
    }

    @Unique
    private static void writeVarLongFull(ByteBuf buf, long value) {
    }
}
