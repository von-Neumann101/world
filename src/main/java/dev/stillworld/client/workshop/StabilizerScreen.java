package dev.stillworld.client.workshop;

import dev.stillworld.workshop.StabilizerBlockEntity;
import dev.stillworld.workshop.StabilizerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class StabilizerScreen extends AbstractContainerScreen<StabilizerMenu> {
    public StabilizerScreen(StabilizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 166;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xff8b8b8b);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff373737);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xffb4b4b4);
        }
        graphics.fill(leftPos + 79, topPos + 37, leftPos + 106, topPos + 49, 0xff45404b);
        int width = menu.progress() * 27 / StabilizerBlockEntity.PROCESSING_TICKS;
        graphics.fill(leftPos + 79, topPos + 37, leftPos + 79 + width, topPos + 49, 0xffbca1eb);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        String key = !menu.authorized() ? "gui.stillworld.stabilizer.unauthorized"
                : menu.active() ? "gui.stillworld.stabilizer.active" : "gui.stillworld.stabilizer.invalid";
        graphics.drawString(font, Component.translatable(key), 8, 58, 0xfff0e9ff, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
