package blindmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class Payloads {
    private Payloads() {}

    /** Клиент -> сервер: изучил блок. */
    public record Learn(BlockPos pos) implements CustomPayload {
        public static final CustomPayload.Id<Learn> ID = new CustomPayload.Id<>(Identifier.of(BlindMod.MOD_ID, "learn"));
        public static final PacketCodec<RegistryByteBuf, Learn> CODEC =
                PacketCodec.tuple(BlockPos.PACKET_CODEC, Learn::pos, Learn::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Клиент -> сервер: стереть память модуля (клавиша G). */
    public record Clear() implements CustomPayload {
        public static final CustomPayload.Id<Clear> ID = new CustomPayload.Id<>(Identifier.of(BlindMod.MOD_ID, "clear"));
        public static final PacketCodec<RegistryByteBuf, Clear> CODEC = PacketCodec.unit(new Clear());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Клиент -> сервер: открыть меню модуля (клавиша J). */
    public record OpenMenu() implements CustomPayload {
        public static final CustomPayload.Id<OpenMenu> ID = new CustomPayload.Id<>(Identifier.of(BlindMod.MOD_ID, "open_menu"));
        public static final PacketCodec<RegistryByteBuf, OpenMenu> CODEC = PacketCodec.unit(new OpenMenu());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Сервер -> клиент: какой модуль сейчас вставлен в слот (со всеми данными). */
    public record ModuleSync(ItemStack stack) implements CustomPayload {
        public static final CustomPayload.Id<ModuleSync> ID = new CustomPayload.Id<>(Identifier.of(BlindMod.MOD_ID, "module_sync"));
        public static final PacketCodec<RegistryByteBuf, ModuleSync> CODEC =
                PacketCodec.tuple(ItemStack.OPTIONAL_PACKET_CODEC, ModuleSync::stack, ModuleSync::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
}
