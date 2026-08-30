package net.mehvahdjukaar.courier_owls.parcel.client;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.parcel.CardboardPackageMenu;
import net.mehvahdjukaar.courier_owls.parcel.SetPackageRecipientMessage;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CardboardPackageScreen extends AbstractContainerScreen<CardboardPackageMenu> {
    private static final Identifier TEXTURE = BirdMod.res("textures/gui/container/cardboard_package.png");

    private static final int BACKGROUND_HEIGHT = 156;
    private static final int LABEL_X = 8;
    private static final int NAME_X = 28;
    private static final int NAME_Y = 46;
    private static final int NAME_WIDTH = 136;
    private static final int NAME_HEIGHT = 12;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFF404040;

    private static final Component RECIPIENT_LABEL = Component.translatable("gui.courier_owls.package_recipient");

    private EditBox nameField;

    public CardboardPackageScreen(CardboardPackageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, DEFAULT_IMAGE_WIDTH, BACKGROUND_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.nameField = new EditBox(this.font, this.leftPos + NAME_X, this.topPos + NAME_Y,
                NAME_WIDTH, NAME_HEIGHT, RECIPIENT_LABEL);
        this.nameField.setMaxLength(CardboardPackageMenu.NAME_LIMIT);
        this.nameField.setBordered(false);
        this.nameField.setTextColor(NAME_COLOR);
        this.nameField.setValue(this.menu.recipient());
        this.addRenderableWidget(this.nameField);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.onClose();
            return true;
        }

        if (this.nameField.keyPressed(event) || this.nameField.canConsumeInput()) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        NetworkHelper.sendToServer(new SetPackageRecipientMessage(this.nameField.getValue()));
        super.onClose();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        graphics.text(this.font, RECIPIENT_LABEL, LABEL_X, NAME_Y + 2, LABEL_COLOR, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, BACKGROUND_TEXTURE_WIDTH, BACKGROUND_TEXTURE_HEIGHT);
    }
}
