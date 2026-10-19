package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Мобы, игроки и предметы на земле не рисуются у слепого (но слышны). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$hideEntities(CallbackInfoReturnable<Boolean> cir) {
        if (BlindClient.blind) cir.setReturnValue(false);
    }
}
