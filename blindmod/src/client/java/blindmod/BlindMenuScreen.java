package blindmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/** Меню модуля памяти (клавиша J). Рисуется программно, без текстур. */
public class BlindMenuScreen extends HandledScreen<BlindMenuHandler> {
    public BlindMenuScreen(BlindMenuHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFFC6C6C6);
        for (Slot s : handler.slots) {
            ctx.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF373737);
            ctx.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        super.drawForeground(ctx, mouseX, mouseY);
        ItemStack st = handler.getSlot(0).getStack();
        Text t = MemoryModuleItem.isModule(st)
                ? Text.translatable("blindmod.menu.used",
                        MemoryModuleItem.fmt(MemoryModuleItem.data(st).entries().size()),
                        MemoryModuleItem.fmt(MemoryModuleItem.capacity(st)))
                : Text.translatable("blindmod.menu.empty");
        ctx.drawText(textRenderer, t, 8, 56, 0x404040, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BlindClient.menuKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
