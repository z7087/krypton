package me.steinborn.krypton.mixin.shared.network.pipeline.prepender;

import me.steinborn.krypton.mod.shared.network.pipeline.MinecraftVarInt21Prepender;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Connection.class)
public class ClientConnectionMixin {
    //? if >=1.20.5 {
    /**
     * @author Andrew Steinborn
     * @reason replace Mojang prepender with a more efficient one
     */
    @org.spongepowered.asm.mixin.Overwrite
    private static io.netty.channel.ChannelOutboundHandler createFrameEncoder(boolean local) {
        if (local) {
            return new net.minecraft.network.LocalFrameEncoder();
        } else {
            return MinecraftVarInt21Prepender.INSTANCE;
        }
    }
    //?} else {
    /*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "configureSerialization", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lio/netty/channel/ChannelPipeline;addLast(Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;"))
    private static io.netty.channel.ChannelPipeline redirectVarint21LengthFieldPrependerNew(
            io.netty.channel.ChannelPipeline instance,
            String name,
            io.netty.channel.ChannelHandler handler,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<io.netty.channel.ChannelPipeline> original
    ) {
        if ("prepender".equals(name) && handler instanceof net.minecraft.network.Varint21LengthFieldPrepender) {
            return original.call(instance, name, MinecraftVarInt21Prepender.INSTANCE);
        }
        return original.call(instance, name, handler);
    }
    *///?}
}
