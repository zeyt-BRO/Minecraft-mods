package blindmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент: слепой игрок или нет. */
public record BlindPayload(boolean blind) implements CustomPayload {
    public static final CustomPayload.Id<BlindPayload> ID =
            new CustomPayload.Id<>(Identifier.of(BlindMod.MOD_ID, "blind"));
    public static final PacketCodec<RegistryByteBuf, BlindPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOL, BlindPayload::blind, BlindPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
