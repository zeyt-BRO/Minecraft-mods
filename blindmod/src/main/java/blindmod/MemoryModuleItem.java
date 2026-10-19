package blindmod;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Модуль памяти. Объём в КБ = сколько блоков можно изучить (1 блок = 1 КБ). Данные лежат на самом предмете. */
public class MemoryModuleItem extends Item {
    public final int capacityKb;

    public MemoryModuleItem(Settings settings, int capacityKb) {
        super(settings);
        this.capacityKb = capacityKb;
    }

    public static boolean isModule(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof MemoryModuleItem;
    }

    public static int capacity(ItemStack s) {
        return ((MemoryModuleItem) s.getItem()).capacityKb;
    }

    public static MemoryData data(ItemStack s) {
        return s.getOrDefault(BlindMod.MEMORY, MemoryData.EMPTY);
    }

    public static String fmt(int kb) {
        return kb >= 1024 ? (kb / 1024) + " МБ" : kb + " КБ";
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("blindmod.menu.used",
                fmt(data(stack).entries().size()), fmt(capacityKb)).formatted(Formatting.GRAY));
    }

    // ---- слияние: перетащить маленький модуль на большой в инвентаре ----

    private static List<MemoryData.Entry> union(ItemStack src, ItemStack dst) {
        Map<String, MemoryData.Entry> map = new LinkedHashMap<>();
        for (MemoryData.Entry e : data(dst).entries()) map.put(e.dim() + "|" + e.pos(), e);
        for (MemoryData.Entry e : data(src).entries()) map.putIfAbsent(e.dim() + "|" + e.pos(), e);
        return new ArrayList<>(map.values());
    }

    public static boolean canMerge(ItemStack src, ItemStack dst) {
        if (!isModule(src) || !isModule(dst)) return false;
        if (capacity(src) >= capacity(dst)) return false;
        if (data(src).entries().isEmpty()) return false;
        return union(src, dst).size() <= capacity(dst);
    }

    public static ItemStack merge(ItemStack src, ItemStack dst) {
        ItemStack out = dst.copy();
        out.set(BlindMod.MEMORY, new MemoryData(union(src, dst)));
        return out;
    }
}
