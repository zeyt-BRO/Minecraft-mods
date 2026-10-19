package blindmod.mixin;

import blindmod.CaneItem;
import blindmod.MemoryModuleItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1) Трость нельзя двигать в инвентаре.
 * 2) Маленький модуль памяти, перетащенный курсором на большой, переносит в него данные.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow @Final public DefaultedList<Slot> slots;
    @Shadow public abstract ItemStack getCursorStack();
    @Shadow public abstract void setCursorStack(ItemStack stack);

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void blindmod$onSlotClick(int slotIndex, int button, SlotActionType actionType,
                                      PlayerEntity player, CallbackInfo ci) {
        if (CaneItem.isCane(getCursorStack())) { ci.cancel(); return; }
        if (slotIndex >= 0 && slotIndex < slots.size() && CaneItem.isCane(slots.get(slotIndex).getStack())) {
            ci.cancel();
            return;
        }
        if (actionType == SlotActionType.SWAP) {
            boolean hotbar = button >= 0 && button < 9 && CaneItem.isCane(player.getInventory().getStack(button));
            boolean offhand = button == 40 && CaneItem.isCane(player.getOffHandStack());
            if (hotbar || offhand) { ci.cancel(); return; }
        }
        // слияние модулей
        if (actionType == SlotActionType.PICKUP && button == 0 && slotIndex >= 0 && slotIndex < slots.size()) {
            ItemStack cursor = getCursorStack();
            Slot slot = slots.get(slotIndex);
            ItemStack target = slot.getStack();
            if (MemoryModuleItem.canMerge(cursor, target)) {
                slot.setStack(MemoryModuleItem.merge(cursor, target));
                setCursorStack(ItemStack.EMPTY);
                ci.cancel();
            }
        }
    }
}
