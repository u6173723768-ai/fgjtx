package dev.thegodmenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class TheGodMenuScreen extends Screen {
    private TheGodMenuClient.Category activeCategory = TheGodMenuClient.Category.MOVEMENT;
    private final Deque<TrailPoint> trail = new ArrayDeque<>();
    private long lastTrailSample;

    private record TrailPoint(int x, int y, long time) {}

    public TheGodMenuScreen() {
        super(Component.literal("The GOD MENU"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int panelWidth = Math.min(690, this.width - 32);
        int panelHeight = Math.min(420, this.height - 32);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int sidebarWidth = 142;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF20A0B11);
        graphics.fill(left, top, left + panelWidth, top + 3, 0xFFFF784A);
        graphics.fill(left, top + 3, left + sidebarWidth, top + panelHeight, 0xE810111A);
        graphics.fill(left + sidebarWidth, top + 3, left + sidebarWidth + 1, top + panelHeight, 0x553C3D4A);

        graphics.drawString(this.font, "THE GOD MENU", left + 18, top + 18, 0xFFFF8B4A, true);
        graphics.drawString(this.font, TheGodMenuClient.getEditionName().toUpperCase() + " EDITION",
                left + panelWidth - 126, top + 20, 0xFFE8E5F0, true);
        graphics.drawString(this.font, "RIGHT SHIFT  •  CLOSE", left + 18, top + panelHeight - 22, 0xFF777987, false);

        int tabY = top + 66;
        for (TheGodMenuClient.Category category : TheGodMenuClient.getCategories()) {
            boolean selected = category == activeCategory;
            int tabTop = tabY;
            int tabBottom = tabY + 29;
            if (selected) {
                graphics.fill(left + 10, tabTop, left + sidebarWidth - 10, tabBottom, 0xFF28202A);
                graphics.fill(left + 10, tabTop, left + 13, tabBottom, 0xFFFF784A);
            }
            graphics.drawString(this.font, category.label, left + 22, tabTop + 10,
                    selected ? 0xFFFFA16F : 0xFFB1B1BE, selected);
            tabY += 37;
        }

        graphics.drawString(this.font, activeCategory.label.toUpperCase(), left + sidebarWidth + 20,
                top + 53, 0xFFEDEAF5, true);
        graphics.drawString(this.font, "Click a module card to toggle it.",
                left + sidebarWidth + 20, top + 69, 0xFF9293A2, false);

        List<TheGodMenuClient.Module> modules = TheGodMenuClient.getModules(activeCategory);
        int contentLeft = left + sidebarWidth + 18;
        int contentTop = top + 91;
        int cardWidth = (panelWidth - sidebarWidth - 54) / 2;
        int cardHeight = 66;
        int gapX = 12;
        int gapY = 10;

        for (int i = 0; i < modules.size(); i++) {
            TheGodMenuClient.Module module = modules.get(i);
            int col = i % 2;
            int row = i / 2;
            int cardX = contentLeft + col * (cardWidth + gapX);
            int cardY = contentTop + row * (cardHeight + gapY);
            int cardRight = cardX + cardWidth;
            int cardBottom = cardY + cardHeight;
            boolean locked = module.isLocked();
            boolean enabled = module.isEnabled();
            boolean hovered = mouseX >= cardX && mouseX <= cardRight && mouseY >= cardY && mouseY <= cardBottom;

            int background = locked ? 0xAA171821 : enabled ? 0xE52B211D : hovered ? 0xDD20222D : 0xCC171923;
            graphics.fill(cardX, cardY, cardRight, cardBottom, background);
            graphics.fill(cardX, cardY, cardX + 2, cardBottom,
                    locked ? 0xFF6F637A : enabled ? 0xFFFF784A : 0xFF3A3C49);

            graphics.drawString(this.font, module.name, cardX + 12, cardY + 11,
                    locked ? 0xFF9992A2 : enabled ? 0xFFFFA16F : 0xFFE5E3EC, true);
            String state = locked ? "PREMIUM" : enabled ? "ON" : "OFF";
            int stateColor = locked ? 0xFFB99BCB : enabled ? 0xFF78D69B : 0xFF8C8F9D;
            graphics.drawString(this.font, state, cardRight - this.font.width(state) - 10, cardY + 11, stateColor, true);

            String description = module.description;
            if (this.font.width(description) > cardWidth - 24) {
                while (!description.isEmpty() && this.font.width(description + "...") > cardWidth - 24) {
                    description = description.substring(0, description.length() - 1);
                }
                description += "...";
            }
            graphics.drawString(this.font, description, cardX + 12, cardY + 34, 0xFF999BA8, false);
        }

        if (modules.isEmpty()) {
            graphics.drawString(this.font, "No modules in this category yet.",
                    contentLeft, contentTop + 8, 0xFF999BA8, false);
        }

        if (isCursorTrailEnabled()) {
            renderCursorTrail(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private boolean isCursorTrailEnabled() {
        return TheGodMenuClient.getModules(TheGodMenuClient.Category.VISUAL).stream()
                .anyMatch(module -> module.id.equals("cursor_trail") && module.isEnabled());
    }

    private void renderCursorTrail(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (now - lastTrailSample >= 18) {
            trail.addLast(new TrailPoint(mouseX, mouseY, now));
            lastTrailSample = now;
        }
        while (!trail.isEmpty() && now - trail.peekFirst().time() > 360) {
            trail.removeFirst();
        }

        int index = 0;
        for (TrailPoint point : trail) {
            int age = (int) (now - point.time());
            int size = Math.max(2, 6 - age / 90);
            int alpha = Math.max(30, 155 - age / 3);
            int color = (alpha << 24) | 0x00FF986D;
            graphics.fill(point.x() - size / 2, point.y() - size / 2,
                    point.x() + size / 2 + 1, point.y() + size / 2 + 1, color);
            index++;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int panelWidth = Math.min(690, this.width - 32);
        int panelHeight = Math.min(420, this.height - 32);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int sidebarWidth = 142;

        int tabY = top + 66;
        for (TheGodMenuClient.Category category : TheGodMenuClient.getCategories()) {
            if (mouseX >= left + 10 && mouseX <= left + sidebarWidth - 10
                    && mouseY >= tabY && mouseY <= tabY + 29) {
                activeCategory = category;
                return true;
            }
            tabY += 37;
        }

        List<TheGodMenuClient.Module> modules = TheGodMenuClient.getModules(activeCategory);
        int contentLeft = left + sidebarWidth + 18;
        int contentTop = top + 91;
        int cardWidth = (panelWidth - sidebarWidth - 54) / 2;
        int cardHeight = 66;
        int gapX = 12;
        int gapY = 10;

        for (int i = 0; i < modules.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int cardX = contentLeft + col * (cardWidth + gapX);
            int cardY = contentTop + row * (cardHeight + gapY);
            if (mouseX >= cardX && mouseX <= cardX + cardWidth
                    && mouseY >= cardY && mouseY <= cardY + cardHeight) {
                TheGodMenuClient.toggle(modules.get(i));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
