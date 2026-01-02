package me.steinborn.krypton.mixin.shared.network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import me.steinborn.krypton.mod.shared.network.util.QuietDecoderException;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Varint21FrameDecoder;
import org.spongepowered.asm.mixin.*;

import java.util.List;

import static me.steinborn.krypton.mod.shared.network.util.WellKnownExceptions.DECODING_FRAME_EMPTY_CACHED;
import static me.steinborn.krypton.mod.shared.network.util.WellKnownExceptions.DECODING_VARINT21_BIG_CACHED;

/**
 * Overrides the SplitterHandler to use optimized packet splitting from Velocity 1.1.0. In addition, this applies a
 * security fix to stop "nullping" attacks.
 * edit: no longer fixes "nullping" attacks as the current implementation throws an exception instead
 */
@Mixin(Varint21FrameDecoder.class)
public class SplitterHandlerMixin {
    @Unique
    private static final int BREAK_AND_IGNORE_READER_INDEX = -1;

    @Shadow
    @Final
    private BandwidthDebugMonitor monitor;

    /**
     * @author z7087
     * @reason Use optimized Velocity varint decoder that reduces bounds checking
     */
    @Overwrite
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!ctx.channel().isActive()) {
            in.clear();
            return;
        }

        int preIndex = in.readerIndex();
        // try to read the length of the packet
        int length = readRawVarInt21(in, preIndex);
        if (length < 0) {
            // 数据包分片，但当前的readRawVarInt21实现不需要resetReaderIndex
            return;
        } else {
            if (length == 0) {
                throw DECODING_FRAME_EMPTY_CACHED;
            }
            // readableBytes is checked in readRawVarInt21
            //if (in.readableBytes() < length) {
            //    in.resetReaderIndex();
            //}
            out.add(in.readRetainedSlice(length));
            if (this.monitor != null) {
                this.monitor.onReceive((in.readerIndex() - preIndex));
            }
        }

    }

    /**
     * Reads a VarInt from the buffer of up to 21 bits in size.
     *
     * @param buffer the buffer to read from
     * @return the VarInt decoded, {@code 0} if no varint could be read
     * @throws QuietDecoderException if the VarInt is too big to be decoded
     */
    @Unique
    private static int readRawVarInt21(ByteBuf buffer, int preIndex) {
        int readableBytes = buffer.writerIndex() - preIndex;
        if (readableBytes < 4) {
            // we don't have enough that we can read a potentially full varint, so fall back to
            // the slow path.
            return readRawVarIntSmallBuf(buffer, preIndex, readableBytes);
        }
        int wholeOrMore = buffer.getIntLE(preIndex);

        // take the last three bytes and check if any of them have the high bit set
        int atStop = ~wholeOrMore & 0x808080;
        if (atStop == 0) {
            // all bytes have the high bit set, so the varint we are trying to decode is too wide
            throw DECODING_VARINT21_BIG_CACHED;
        }

        int bitsToKeep = Integer.numberOfTrailingZeros(atStop << 1);
        int bytesToKeep = bitsToKeep >> 3;

        // remove all bits we don't need to keep, a trick from
        // https://github.com/netty/netty/pull/14050#issuecomment-2107750734:
        //
        // > The idea is that thisVarintMask has 0s above the first one of firstOneOnStop, and 1s at
        // > and below it. For example if firstOneOnStop is 0x800080 (where the last 0x80 is the only
        // > one that matters), then thisVarintMask is 0xFF.
        //
        // this is also documented in Hacker's Delight, section 2-1 "Manipulating Rightmost Bits"
        int preservedBytes = wholeOrMore & (atStop ^ (atStop - 1));
        //int preservedBytes = wholeOrMore & (~(-1 << bitsToKeep));

        // merge together using this trick: https://github.com/netty/netty/pull/14050#discussion_r1597896639
        //? if java: >= 19 {
        preservedBytes = Integer.compress(preservedBytes, 0x7F7F7F);
        //? } else {
        /*// i dont think we should use SWAR on varint21 decoding
        //preservedBytes = (preservedBytes & 0x007F007F) | ((preservedBytes & 0x00007F00) >> 1);
        //preservedBytes = (preservedBytes & 0x00003FFF) | ((preservedBytes & 0x3FFF0000) >> 2);
        preservedBytes = (preservedBytes & 0x7F) |
                ((preservedBytes & 0x7F00) >> 7) |
                ((preservedBytes & 0x7F0000) >> 14);
        *///? }
        if ((readableBytes - bytesToKeep) < preservedBytes) {
            return BREAK_AND_IGNORE_READER_INDEX;
        }
        buffer.readerIndex(preIndex + bytesToKeep);
        return preservedBytes;
    }

    @Unique
    private static int readRawVarIntSmallBuf(ByteBuf buffer, int preIndex, int readableBytes) {
        // 假设：客户端发送由正常的VarInt编码器编码的长度 (即不会出现用n+m字节编码本应使用n字节编码的VarInt)
        // 由于前面检查过buffer.readableBytes() < 4，这里最多只能读取3个字节
        // 但香草端在此类中会检查buffer.readableBytes()是否<VarInt编码的数据包报文长度 若否则resetReaderIndex 可能是在处理数据包分片的情况
        // 此处VarInt长度必然占1+字节 但只有3字节可读 所以只有两种情况 1是VarInt必然为1字节长度 2是数据包分片了
        if (readableBytes > 1) {
            int tmp = buffer.getByte(preIndex) & 0xFF;
            // 若tmp设置了0x80 或tmp+1>readableBytes(即tmp>=readableBytes)
            if (tmp >= readableBytes) {
                return BREAK_AND_IGNORE_READER_INDEX;
            }
            buffer.readerIndex(preIndex + 1);
            return tmp;
        } else {
            return BREAK_AND_IGNORE_READER_INDEX;
        }
    }
}
