package blindmod.mixin;

import blindmod.BlindClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Цвет фона (того, что видно там, где блоков нет) делаем чёрным. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private static void blindmod$blackBackground(CallbackInfo ci) {
        if (BlindClient.blind) RenderSystem.clearColor(0f, 0f, 0f, 0f);
    }
}
