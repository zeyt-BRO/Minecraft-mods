package blindmod;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BlindMod implements ModInitializer {
    public static final String MOD_ID = "blindmod";
    /** Слепота хранится как командный тег игрока (сохраняется в его данных сам). */
    public static final String BLIND_TAG = "blindmod_blind";
    public static final String GIFT_TAG = "blindmod_got_module";

    public static final Item CANE = Registry.register(
            Registries.ITEM, Identifier.of(MOD_ID, "cane"),
            new CaneItem(new Item.Settings().maxCount(1)));

    /** Данные модуля (список изученных блоков) лежат на самом предмете. */
    public static final ComponentType<MemoryData> MEMORY = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of(MOD_ID, "memory"),
            ComponentType.<MemoryData>builder().codec(MemoryData.CODEC).packetCodec(MemoryData.PACKET_CODEC).build());

    /** Объёмы модулей в КБ: 1 КБ = 1 изученный блок. */
    public static final int[] MODULE_KB = {16, 32, 64, 128, 256, 512, 1024, 2048, 5120, 10240};
    public static final String[] MODULE_ID = {"16k", "32k", "64k", "128k", "256k", "512k", "1m", "2m", "5m", "10m"};
    public static final Item[] MODULES = new Item[MODULE_KB.length];
    static {
        for (int i = 0; i < MODULES.length; i++) {
            MODULES[i] = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "memory_module_" + MODULE_ID[i]),
                    new MemoryModuleItem(new Item.Settings().maxCount(1), MODULE_KB[i]));
        }
    }

    /** Модуль, вставленный в слот меню J (хранится у игрока, переживает смерть). */
    public static final AttachmentType<ItemStack> MODULE_SLOT = AttachmentRegistry.<ItemStack>builder()
            .persistent(ItemStack.OPTIONAL_CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier.of(MOD_ID, "module_slot"));

    public static final ScreenHandlerType<BlindMenuHandler> MENU_TYPE = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "menu"),
            new ScreenHandlerType<>(BlindMenuHandler::new, FeatureSet.empty()));

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(BlindPayload.ID, BlindPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.ModuleSync.ID, Payloads.ModuleSync.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.Learn.ID, Payloads.Learn.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.Clear.ID, Payloads.Clear.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.OpenMenu.ID, Payloads.OpenMenu.CODEC);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(CANE);
            for (Item m : MODULES) entries.add(m);
        });

        // --- сеть: клиент просит ---
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Learn.ID,
                (payload, ctx) -> learn(ctx.player(), payload.pos()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Clear.ID,
                (payload, ctx) -> clearModule(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.OpenMenu.ID,
                (payload, ctx) -> openMenu(ctx.player()));

        // выдача при входе / респавне и синхронизация
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            ensureCane(p);
            if (!p.getCommandTags().contains(GIFT_TAG)) {
                p.addCommandTag(GIFT_TAG);
                giveItem(p, new ItemStack(MODULES[0]));
            }
            ServerPlayNetworking.send(p, new BlindPayload(isBlind(p)));
            ServerPlayNetworking.send(p, new Payloads.ModuleSync(getModule(p)));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            ensureCane(newP);
            ServerPlayNetworking.send(newP, new BlindPayload(isBlind(newP)));
            ServerPlayNetworking.send(newP, new Payloads.ModuleSync(getModule(newP)));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ensureCane(p);
            }
        });

        // /blind on|off  и  /blind <игрок> on|off
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(CommandManager.literal("blind")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("on")
                                .executes(c -> run(c, c.getSource().getPlayerOrThrow(), true)))
                        .then(CommandManager.literal("off")
                                .executes(c -> run(c, c.getSource().getPlayerOrThrow(), false)))
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.literal("on")
                                        .executes(c -> run(c, EntityArgumentType.getPlayer(c, "target"), true)))
                                .then(CommandManager.literal("off")
                                        .executes(c -> run(c, EntityArgumentType.getPlayer(c, "target"), false))))));
    }

    private static int run(CommandContext<ServerCommandSource> c, ServerPlayerEntity target, boolean on) {
        setBlind(target, on);
        c.getSource().sendFeedback(() -> Text.translatable(
                on ? "blindmod.cmd.on" : "blindmod.cmd.off", target.getDisplayName()), true);
        return 1;
    }

    public static boolean isBlind(ServerPlayerEntity p) {
        return p.getCommandTags().contains(BLIND_TAG);
    }

    public static void setBlind(ServerPlayerEntity p, boolean blind) {
        if (blind) p.addCommandTag(BLIND_TAG); else p.removeCommandTag(BLIND_TAG);
        ServerPlayNetworking.send(p, new BlindPayload(blind));
    }

    // ---------- модуль в слоте ----------

    public static ItemStack getModule(ServerPlayerEntity p) {
        return p.getAttachedOrElse(MODULE_SLOT, ItemStack.EMPTY);
    }

    public static void setModule(ServerPlayerEntity p, ItemStack stack) {
        p.setAttached(MODULE_SLOT, stack);
        ServerPlayNetworking.send(p, new Payloads.ModuleSync(stack));
    }

    private static void openMenu(ServerPlayerEntity p) {
        if (p.currentScreenHandler != p.playerScreenHandler) return;
        SimpleInventory inv = new SimpleInventory(1);
        inv.setStack(0, getModule(p));
        inv.addListener(sender -> setModule(p, sender.getStack(0)));
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, player) -> new BlindMenuHandler(syncId, playerInv, inv),
                Text.translatable("blindmod.menu")));
    }

    private static void clearModule(ServerPlayerEntity p) {
        ItemStack stack = getModule(p);
        if (!MemoryModuleItem.isModule(stack)) return;
        stack.set(MEMORY, MemoryData.EMPTY);
        setModule(p, stack);
        p.sendMessage(Text.translatable("blindmod.cleared"), true);
    }

    /** Игрок изучил блок тростью: записываем в модуль (1 КБ), если есть место. */
    private static void learn(ServerPlayerEntity p, BlockPos pos) {
        if (!isBlind(p)) return;
        ItemStack stack = getModule(p);
        if (!MemoryModuleItem.isModule(stack)) return;
        BlockPos pp = p.getBlockPos();
        if (Math.abs(pos.getX() - pp.getX()) > 2 || Math.abs(pos.getY() - pp.getY()) > 2
                || Math.abs(pos.getZ() - pp.getZ()) > 2) return;
        ServerWorld world = p.getServerWorld();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;

        String dim = world.getRegistryKey().getValue().toString();
        long key = pos.asLong();
        MemoryData.Entry entry = new MemoryData.Entry(dim, key, Registries.BLOCK.getId(state.getBlock()).toString());

        List<MemoryData.Entry> list = new ArrayList<>(MemoryModuleItem.data(stack).entries());
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            MemoryData.Entry e = list.get(i);
            if (e.pos() == key && e.dim().equals(dim)) { idx = i; break; }
        }
        if (idx >= 0) {
            list.set(idx, entry); // повторное изучение: обновляем, место не занимаем
        } else {
            if (list.size() >= MemoryModuleItem.capacity(stack)) {
                p.sendMessage(Text.translatable("blindmod.full"), true);
                return;
            }
            list.add(entry);
        }
        stack.set(MEMORY, new MemoryData(list));
        setModule(p, stack);
    }

    private static void giveItem(ServerPlayerEntity p, ItemStack stack) {
        if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
    }

    /** Гарантирует, что у игрока есть трость (при входе, респавне, потере). */
    public static void ensureCane(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        if (CaneItem.isCane(p.currentScreenHandler.getCursorStack())) return;
        if (inv.contains(CaneItem::isCane)) return;
        ItemStack cane = new ItemStack(CANE);
        if (!inv.insertStack(cane)) {
            ItemStack old = inv.getStack(8);
            inv.setStack(8, new ItemStack(CANE));
            p.dropItem(old, false);
        }
    }
}
