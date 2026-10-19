package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Мир уже нарисован, а интерфейс ещё нет: закрашиваем экран чёрным, хотбар/чат рисуются поверх. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void blindmod$blackout(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (BlindClient.blind) {
            ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 0xFF000000);
        }
    }
}
