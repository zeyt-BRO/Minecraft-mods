package blindmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlindClient implements ClientModInitializer {
    /** Слепой ли сейчас этот игрок (приходит с сервера). Читается и из потока перестройки чанков. */
    public static volatile boolean blind = false;
    /** Изученные блоки в текущем измерении: BlockPos.asLong -> id блока. Читается из потока чанков. */
    public static volatile Map<Long, String> known = Map.of();

    public static KeyBinding menuKey;
    public static KeyBinding clearKey;

    private static final int SCAN_TICKS = 100;   // 5 секунд
    private static final int RESULT_TICKS = 60;

    /** kind: 0 = воздух, 1 = блок (key = BlockPos.asLong), 2 = существо (key = id). */
    private record Target(int kind, long key) {
        static final Target AIR = new Target(0, 0);
    }

    private static ItemStack clientModule = ItemStack.EMPTY;
    private static boolean dirty = false;
    private static RegistryKey<World> lastDim = null;

    private static Target current = Target.AIR;
    private static Target scanTarget = null;
    private static int scanTicks = 0;
    private static int cooldown = 0;
    private static Target resultTarget = null;
    private static Text resultText = null;
    private static int resultTicks = 0;
    private static long clearConfirmUntil = 0;

    /** Нужно ли рисовать этот блок (изучен ли он). Безопасно вызывать из любого потока. */
    public static boolean isKnown(BlockPos pos) {
        return known.containsKey(pos.asLong());
    }

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blindmod.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.blindmod"));
        clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blindmod.clear", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.blindmod"));

        HandledScreens.register(BlindMod.MENU_TYPE, BlindMenuScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(BlindPayload.ID, (payload, ctx) -> {
            blind = payload.blind();
            if (!blind) cancelScan();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) mc.worldRenderer.reload(); // перестроить меши чанков
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.ModuleSync.ID, (payload, ctx) -> {
            clientModule = payload.stack();
            dirty = true;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            blind = false;
            known = Map.of();
            clientModule = ItemStack.EMPTY;
            lastDim = null;
            cancelScan();
        });
        ClientTickEvents.END_CLIENT_TICK.register(BlindClient::tick);

        UseBlockCallback.EVENT.register((p, w, hand, hit) ->
                w.isClient && intercept(p, hand) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((p, w, hand, e, hit) ->
                w.isClient && intercept(p, hand) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((p, w, hand) -> {
            ItemStack s = p.getStackInHand(hand);
            return w.isClient && intercept(p, hand) ? TypedActionResult.fail(s) : TypedActionResult.pass(s);
        });

        HudRenderCallback.EVENT.register(BlindClient::renderHud);
    }

    private static boolean intercept(PlayerEntity p, Hand hand) {
        if (!blind || !CaneItem.isCane(p.getStackInHand(hand))) return false;
        if (hand == Hand.MAIN_HAND && scanTarget == null && cooldown == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Target t = computeTarget(mc);
            if (t.kind() == 1) {
                if (!MemoryModuleItem.isModule(clientModule)) {
                    mc.player.sendMessage(Text.translatable("blindmod.nomodule"), true);
                    cooldown = 10;
                    return true;
                }
                boolean already = known.containsKey(t.key());
                if (!already && MemoryModuleItem.data(clientModule).entries().size()
                        >= MemoryModuleItem.capacity(clientModule)) {
                    mc.player.sendMessage(Text.translatable("blindmod.full"), true);
                    cooldown = 10;
                    return true;
                }
            }
            scanTarget = t;
            scanTicks = 0;
        }
        return true;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (blind) mc.options.hudHidden = false; // F1 не должен открывать мир

        RegistryKey<World> dim = mc.world.getRegistryKey();
        if (dirty || dim != lastDim) {
            lastDim = dim;
            rebuildKnown(mc);
        }

        while (menuKey.wasPressed()) {
            ClientPlayNetworking.send(new Payloads.OpenMenu());
        }
        while (clearKey.wasPressed()) {
            long now = mc.world.getTime();
            if (clearConfirmUntil > now) {
                ClientPlayNetworking.send(new Payloads.Clear());
                clearConfirmUntil = 0;
            } else {
                clearConfirmUntil = now + 60;
                mc.player.sendMessage(Text.translatable("blindmod.clear.confirm"), true);
            }
        }

        current = computeTarget(mc);
        if (cooldown > 0) cooldown--;
        if (resultTicks > 0) resultTicks--;

        if (scanTarget != null) {
            boolean ok = blind && CaneItem.isCane(mc.player.getMainHandStack()) && current.equals(scanTarget);
            if (!ok) cancelScan();
            else if (++scanTicks >= SCAN_TICKS) finishScan(mc);
        }
    }

    /** Пересобираем набор изученных блоков из вставленного модуля и перерисовываем затронутые участки. */
    private static void rebuildKnown(MinecraftClient mc) {
        dirty = false;
        Map<Long, String> fresh = new ConcurrentHashMap<>();
        if (MemoryModuleItem.isModule(clientModule)) {
            String dim = mc.world.getRegistryKey().getValue().toString();
            for (MemoryData.Entry e : MemoryModuleItem.data(clientModule).entries()) {
                if (e.dim().equals(dim)) fresh.put(e.pos(), e.block());
            }
        }
        Map<Long, String> old = known;
        known = fresh;

        Set<Long> changed = new HashSet<>();
        for (Long k : fresh.keySet()) if (!old.containsKey(k)) changed.add(k);
        for (Long k : old.keySet()) if (!fresh.containsKey(k)) changed.add(k);
        if (changed.isEmpty() || !blind) return;
        if (changed.size() > 2000) {
            mc.worldRenderer.reload();
        } else {
            for (long k : changed) {
                BlockPos pos = BlockPos.fromLong(k);
                BlockState st = mc.world.getBlockState(pos);
                mc.world.updateListeners(pos, st, st, 8);
            }
        }
    }

    private static void cancelScan() {
        scanTarget = null;
        scanTicks = 0;
    }

    private static void finishScan(MinecraftClient mc) {
        Target t = scanTarget;
        Text name;
        if (t.kind() == 1) {
            BlockPos pos = BlockPos.fromLong(t.key());
            name = mc.world.getBlockState(pos).getBlock().getName();
            ClientPlayNetworking.send(new Payloads.Learn(pos)); // сервер запишет в модуль
        } else if (t.kind() == 2) {
            Entity e = mc.world.getEntityById((int) t.key());
            name = e != null ? e.getName() : Text.translatable("blindmod.air");
        } else {
            name = Text.translatable("blindmod.air");
        }
        resultTarget = t;
        resultText = name;
        resultTicks = RESULT_TICKS;
        cooldown = 10;
        cancelScan();
    }

    /** Что сейчас под прицелом, но только в радиусе 1 блока (3x3x3 вокруг игрока). Дальше = воздух. */
    private static Target computeTarget(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return Target.AIR;
        if (mc.crosshairTarget instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            if (inRange(p, e.getBlockPos())) return new Target(2, e.getId());
        }
        HitResult h = p.raycast(5.0, 1.0f, true); // true = видеть жидкости
        if (h instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK
                && inRange(p, bh.getBlockPos())) {
            return new Target(1, bh.getBlockPos().asLong());
        }
        return Target.AIR;
    }

    private static boolean inRange(ClientPlayerEntity p, BlockPos pos) {
        BlockPos pp = p.getBlockPos();
        return Math.abs(pos.getX() - pp.getX()) <= 1
                && Math.abs(pos.getY() - pp.getY()) <= 1
                && Math.abs(pos.getZ() - pp.getZ()) <= 1;
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!blind) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer tr = mc.textRenderer;

        if (scanTarget != null) {
            int pct = Math.min(100, scanTicks * 100 / SCAN_TICKS);
            int bw = 100, bx = w / 2 - bw / 2, by = h - 72;
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFFAAAAAA);
            ctx.fill(bx, by, bx + bw, by + 6, 0xFF000000);
            ctx.fill(bx, by, bx + pct, by + 6, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, pct + "%", w / 2, by - 11, 0xFFFFFF);
            return;
        }

        Text text = null;
        if (current.kind() == 1) {
            String id = known.get(current.key());
            if (id != null) text = Registries.BLOCK.get(Identifier.of(id)).getName();
        }
        if (text == null && resultTicks > 0 && current.equals(resultTarget)) text = resultText;
        if (text != null) ctx.drawCenteredTextWithShadow(tr, text, w / 2, h - 72, 0xFFFFFF);
    }
}
