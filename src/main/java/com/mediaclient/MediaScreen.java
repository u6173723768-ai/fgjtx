package com.mediaclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MediaScreen extends Screen {
    public MediaScreen() {
        super(Component.literal("Media Client"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int left = this.width / 2 - 180;
        int top = this.height / 2 - 120;
        graphics.fill(left, top, left + 360, top + 240, 0xE5101018);
        graphics.drawString(this.font, Component.literal("MEDIA CLIENT"), left + 20, top + 20, 0xFFB46CFF, true);
        graphics.drawString(this.font, Component.literal("Right Shift • Toggle GUI"), left + 20, top + 45, 0xFFE0E0E8);
        graphics.drawString(this.font, Component.literal("Visual / ESP / World / Automation"), left + 20, top + 80, 0xFFB8B8C8);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEYSYM.getValue()
                && event.scancode() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
