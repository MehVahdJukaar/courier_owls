package net.mehvahdjukaar.courier_owls.owls.client.gui;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.blocks.BirdNestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class BirdNestScreen extends AbstractContainerScreen<BirdNestMenu> {
    private static final Identifier TEXTURE = BirdMod.res("textures/gui/container/bird_house.png");

    private static final int BACKGROUND_HEIGHT = 150;

    public BirdNestScreen(BirdNestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, DEFAULT_IMAGE_WIDTH, BACKGROUND_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
    }
}
