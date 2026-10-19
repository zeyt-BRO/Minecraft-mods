package blindmod.mixin;

import blindmod.CaneItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Трость нельзя выбросить (Q) и она не выпадает при смерти. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void blindmod$noDropCane(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                     CallbackInfoReturnable<ItemEntity> cir) {
        if (!CaneItem.isCane(stack)) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        // живой игрок на сервере нажал Q: возвращаем трость на место
        if (!self.isDead() && !self.getWorld().isClient) {
            PlayerInventory inv = self.getInventory();
            ItemStack copy = stack.copy();
            int sel = inv.selectedSlot;
            if (inv.getStack(sel).isEmpty()) inv.setStack(sel, copy); else inv.insertStack(copy);
        }
        // при смерти просто пропадает, новая выдаётся при респавне
        cir.setReturnValue(null);
    }
}
