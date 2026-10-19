package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Неизученные блоки (и жидкости) не попадают в меш чанка, значит остаются чёрными. */
@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$hideBlock(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
                                    VertexConsumer vertexConsumer, boolean cull, Random random, CallbackInfo ci) {
        if (BlindClient.blind && !BlindClient.isKnown(pos)) ci.cancel();
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$hideFluid(BlockPos pos, BlockRenderView world, VertexConsumer vertexConsumer,
                                    BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (BlindClient.blind && !BlindClient.isKnown(pos)) ci.cancel();
    }
}
