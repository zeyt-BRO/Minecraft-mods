package blindmod.mixin;

import blindmod.BlindClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRendererRegion.class)
public abstract class ChunkRendererRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true, require = 1)
    private void blindmod$hideBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (BlindClient.blind && !BlindClient.isKnown(pos)) cir.setReturnValue(Blocks.AIR.getDefaultState());
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true, require = 0)
    private void blindmod$hideFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (BlindClient.blind && !BlindClient.isKnown(pos)) cir.setReturnValue(Fluids.EMPTY.getDefaultState());
    }
}