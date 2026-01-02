package me.steinborn.krypton.mod.shared.network.pipeline;

import com.velocitypowered.natives.encryption.JavaVelocityCipher;
import com.velocitypowered.natives.util.Natives;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

import me.steinborn.krypton.mod.shared.network.util.VarIntUtil;
import me.steinborn.krypton.mod.shared.network.util.WellKnownExceptions;

@ChannelHandler.Sharable
public class MinecraftVarInt21Prepender extends MessageToMessageEncoder<ByteBuf> {
    public static final MinecraftVarInt21Prepender INSTANCE = new MinecraftVarInt21Prepender();
    static final boolean IS_JAVA_CIPHER = Natives.cipher.get() == JavaVelocityCipher.FACTORY;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        final int length = msg.readableBytes();
        final int varintLength = VarIntUtil.getVarIntLength(length);
        if (varintLength > 3) {
            throw WellKnownExceptions.ENCODING_VARINT21_BIG_CACHED;
        }

        // this isn't optimal (ideally, we would use the trick Velocity uses and combine the prepender and
        // compressor into one), but to maximize mod compatibility, we do this instead
        final ByteBuf lenBuf = IS_JAVA_CIPHER
                ? ctx.alloc().heapBuffer(varintLength)
                : ctx.alloc().directBuffer(varintLength);

        VarIntUtil.write(lenBuf, length);
        out.add(lenBuf);
        out.add(msg.retain());
    }
}
