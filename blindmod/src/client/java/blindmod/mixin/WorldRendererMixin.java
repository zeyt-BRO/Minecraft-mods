package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Небо, облака, погода и рамка выделения блока скрыты у слепого. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$noSky(CallbackInfo ci) {
        if (BlindClient.blind) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$noClouds(CallbackInfo ci) {
        if (BlindClient.blind) ci.cancel();
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$noWeather(CallbackInfo ci) {
        if (BlindClient.blind) ci.cancel();
    }

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$noOutline(CallbackInfo ci) {
        if (BlindClient.blind) ci.cancel();
    }
}
