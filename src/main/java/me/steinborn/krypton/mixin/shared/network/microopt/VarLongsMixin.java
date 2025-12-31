package me.steinborn.krypton.mixin.shared.network.microopt;


import io.netty.buffer.ByteBuf;
import me.steinborn.krypton.mod.shared.KryptonSharedInitializer;
import me.steinborn.krypton.mod.shared.network.util.VarLongUtil;
import net.minecraft.network.VarLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

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
        KryptonSharedInitializer.getConfig().getVarLongProvider().write(buf, value);

        return buf;
    }
}
