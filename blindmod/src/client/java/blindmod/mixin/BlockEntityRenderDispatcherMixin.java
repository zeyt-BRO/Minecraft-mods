package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Сундуки, таблички и т.п. рисуются только если изучены. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$hideBlockEntity(BlockEntity be, float tickDelta, MatrixStack matrices,
                                          VertexConsumerProvider consumers, CallbackInfo ci) {
        if (BlindClient.blind && !BlindClient.isKnown(be.getPos())) ci.cancel();
    }
}
