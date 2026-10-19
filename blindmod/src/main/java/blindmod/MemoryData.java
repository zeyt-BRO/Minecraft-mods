package blindmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;

/** Содержимое модуля памяти: список изученных блоков (1 запись = 1 КБ). */
public record MemoryData(List<Entry> entries) {
    public static final MemoryData EMPTY = new MemoryData(List.of());

    /** dim = id измерения, pos = BlockPos.asLong(), block = id блока на момент изучения. */
    public record Entry(String dim, long pos, String block) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("d").forGetter(Entry::dim),
                Codec.LONG.fieldOf("p").forGetter(Entry::pos),
                Codec.STRING.fieldOf("b").forGetter(Entry::block)
        ).apply(i, Entry::new));

        public static final PacketCodec<ByteBuf, Entry> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Entry::dim,
                PacketCodecs.VAR_LONG, Entry::pos,
                PacketCodecs.STRING, Entry::block,
                Entry::new);
    }

    public static final Codec<MemoryData> CODEC =
            Entry.CODEC.listOf().xmap(MemoryData::new, MemoryData::entries);

    public static final PacketCodec<ByteBuf, MemoryData> PACKET_CODEC =
            Entry.PACKET_CODEC.collect(PacketCodecs.toList()).xmap(MemoryData::new, MemoryData::entries);
}
