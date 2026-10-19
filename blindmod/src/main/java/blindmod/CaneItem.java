package blindmod;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Трость. Вся логика сканирования на клиенте (BlindClient), сам предмет простой. */
public class CaneItem extends Item {
    public CaneItem(Settings settings) {
        super(settings);
    }

    public static boolean isCane(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(BlindMod.CANE);
    }
}
