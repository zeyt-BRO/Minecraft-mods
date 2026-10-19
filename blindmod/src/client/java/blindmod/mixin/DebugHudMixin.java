package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Слепому не рисуем F3 (там видно блок под прицелом и координаты). */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void blindmod$noDebug(DrawContext context, CallbackInfo ci) {
        if (BlindClient.blind) ci.cancel();
    }
}
