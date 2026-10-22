package com.mediaclient.gui;

import com.mediaclient.MediaClient;
import com.mediaclient.config.MediaConfig;
import com.mediaclient.module.ClientModule;
import com.mediaclient.module.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MediaClickScreen extends Screen {
    private static final int PANEL_W = 740, PANEL_H = 460, SIDEBAR_W = 184;
    private static final int DIM = 0xC9070810, OUTER = 0xFF39205E, PANEL = 0xFF090B16, SIDEBAR = 0xFF080A13;
    private static final int SURFACE = 0xFF101321, SURFACE_HOVER = 0xFF171B2D, BORDER = 0xFF242944;
    private static final int ACCENT = 0xFF9C4DFF, ACCENT_BRIGHT = 0xFFBD83FF, ACCENT_WASH = 0xFF281447;
    private static final int TEXT = 0xFFF5F2FF, MUTED = 0xFF9CA5C2, GREEN = 0xFF68E5B1;
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("media_client", "textures/gui/media_logo.png");

    private ModuleCategory selected = ModuleCategory.ESP;
    private EditBox schematicName;
    private int panelX, panelY;
    private int espFilterPage;

    public MediaClickScreen() { super(Component.literal("Media Client")); }
    private int contentX() { return panelX + SIDEBAR_W + 16; }
    private int settingsX() { return panelX + 463; }
    private int settingsBaseY() { return panelY + 121; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(0, (height - PANEL_H) / 2);
        schematicName = null;
        if (selected == ModuleCategory.AUTOMATION) {
            int sx = settingsX() + 8, inputY = panelY + PANEL_H - 103;
            schematicName = new EditBox(font, sx, inputY, 162, 20, Component.literal("Schematic filename"));
            schematicName.setHint(Component.literal("example.schem"));
            addRenderableWidget(schematicName);
            addRenderableWidget(Button.builder(Component.literal("Load"), b -> loadSchematic()).bounds(sx + 166, inputY, 73, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Start / Pause"), b -> toggleBuilder()).bounds(sx, inputY + 28, 116, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Stop"), b -> MediaClient.SCHEMATIC_BUILDER.stop()).bounds(sx + 120, inputY + 28, 119, 20).build());
        }
    }
    private void loadSchematic() {
        if (schematicName == null) return;
        String name = schematicName.getValue().trim();
        if (!name.isBlank()) MediaClient.SCHEMATIC_BUILDER.loadByName(name);
    }
    private void toggleBuilder() {
        if (!MediaClient.SCHEMATIC_BUILDER.isLoaded() && schematicName != null && !schematicName.getValue().isBlank()) loadSchematic();
        if (MediaClient.SCHEMATIC_BUILDER.isLoaded()) MediaClient.SCHEMATIC_BUILDER.toggleBuilding();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(0, (height - PANEL_H) / 2);
        int px = panelX, py = panelY, sx = settingsX(), cx = contentX(), right = panelX + PANEL_W;
        g.fill(0, 0, width, height, DIM);
        g.fill(px - 3, py - 3, right + 3, py + PANEL_H + 3, 0x88000000);
        g.fill(px - 1, py - 1, right + 1, py + PANEL_H + 1, OUTER);
        g.fill(px, py, right, py + PANEL_H, PANEL);
        g.fill(px, py, right, py + 2, ACCENT);
        g.fill(px, py + 2, px + SIDEBAR_W, py + PANEL_H, SIDEBAR);
        g.fill(px + SIDEBAR_W, py + 2, px + SIDEBAR_W + 1, py + PANEL_H, BORDER);
        g.fill(px + 14, py + 10, px + 55, py + 50, 0xFF100D1D);
        g.fill(px + 14, py + 10, px + 55, py + 11, 0xFF6E31B5);
        drawLogo(g, px + 17, py + 11, 35);
        g.drawString(font, "MEDIA", px + 61, py + 15, TEXT, false);
        g.drawString(font, "CLIENT", px + 61, py + 29, ACCENT_BRIGHT, false);
        g.drawString(font, "PLAY SMARTER", px + 19, py + 52, MUTED, false);
        g.fill(px + 14, py + 69, px + SIDEBAR_W - 14, py + 70, BORDER);

        int categoryY = py + 82;
        for (ModuleCategory category : ModuleCategory.values()) {
            boolean active = category == selected;
            boolean hover = mouseX >= px + 10 && mouseX <= px + SIDEBAR_W - 11 && mouseY >= categoryY - 4 && mouseY <= categoryY + 23;
            if (active) {
                g.fill(px + 9, categoryY - 4, px + SIDEBAR_W - 10, categoryY + 23, ACCENT_WASH);
                g.fill(px + 9, categoryY - 4, px + 12, categoryY + 23, ACCENT);
                g.fill(px + 14, categoryY - 2, px + SIDEBAR_W - 14, categoryY - 1, 0x665C2C92);
            } else if (hover) g.fill(px + 9, categoryY - 4, px + SIDEBAR_W - 10, categoryY + 23, SURFACE_HOVER);
            g.drawCenteredString(font, categoryGlyph(category), px + 27, categoryY + 4, active ? ACCENT_BRIGHT : MUTED);
            g.drawString(font, category.title(), px + 44, categoryY + 5, active ? TEXT : MUTED, false);
            categoryY += 31;
        }
        g.fill(px + 14, py + PANEL_H - 59, px + SIDEBAR_W - 14, py + PANEL_H - 58, BORDER);
        drawAvatar(g, px + 20, py + PANEL_H - 45);
        g.drawString(font, "Media Client", px + 42, py + PANEL_H - 44, TEXT, false);
        g.drawString(font, "v1.0.0  •  Fabric", px + 42, py + PANEL_H - 31, MUTED, false);
        g.drawString(font, "RIGHT SHIFT  /  ESC", px + 18, py + PANEL_H - 12, ACCENT_BRIGHT, false);

        g.drawString(font, selected.title().toUpperCase(), cx, py + 17, TEXT, false);
        g.drawString(font, categorySubtitle(selected), cx, py + 33, MUTED, false);
        int enabled = (int) MediaClient.MODULES.inCategory(selected).stream().filter(ClientModule::enabled).count();
        String moduleCount = enabled + " ACTIVE";
        int pillW = font.width(moduleCount) + 16, pillRight = right - 39;
        g.fill(pillRight - pillW, py + 14, pillRight, py + 32, enabled > 0 ? ACCENT_WASH : SURFACE);
        g.drawString(font, moduleCount, pillRight - pillW + 8, py + 20, enabled > 0 ? ACCENT_BRIGHT : MUTED, false);
        boolean closeHover = mouseX >= right - 34 && mouseX <= right - 8 && mouseY >= py + 7 && mouseY <= py + 34;
        if (closeHover) g.fill(right - 34, py + 7, right - 8, py + 34, SURFACE_HOVER);
        g.drawCenteredString(font, "×", right - 21, py + 15, closeHover ? ACCENT_BRIGHT : MUTED);
        g.fill(px + SIDEBAR_W + 14, py + 57, right - 14, py + 58, BORDER);

        int listLeft = cx - 4, listRight = sx - 9, listTop = py + 73, listBottom = py + PANEL_H - 17;
        drawCard(g, listLeft, listTop, listRight - listLeft, listBottom - listTop, SURFACE);
        g.drawString(font, "MODULES", listLeft + 11, listTop + 11, ACCENT_BRIGHT, false);
        g.drawString(font, "Toggle a feature", listLeft + 11, listTop + 25, MUTED, false);
        g.fill(listLeft + 9, listTop + 41, listRight - 9, listTop + 42, BORDER);
        List<ClientModule> modules = MediaClient.MODULES.inCategory(selected);
        int rowY = listTop + 50;
        for (ClientModule module : modules) {
            int rowH = 58;
            boolean hover = mouseX >= listLeft + 8 && mouseX <= listRight - 8 && mouseY >= rowY && mouseY <= rowY + rowH - 3;
            drawCard(g, listLeft + 8, rowY, listRight - listLeft - 16, rowH - 3, hover ? SURFACE_HOVER : PANEL);
            if (module.enabled()) g.fill(listLeft + 8, rowY + 5, listLeft + 10, rowY + rowH - 8, ACCENT_BRIGHT);
            g.drawString(font, ellipsize(module.name(), 154), listLeft + 17, rowY + 8, TEXT, false);
            g.drawString(font, ellipsize(module.description(), 166), listLeft + 17, rowY + 24, MUTED, false);
            drawSwitch(g, listRight - 38, rowY + 38, module.enabled());
            rowY += 64;
        }
        if (modules.isEmpty()) {
            g.drawCenteredString(font, "NO MODULES", (listLeft + listRight) / 2, listTop + 73, MUTED);
            g.drawCenteredString(font, "in this category yet", (listLeft + listRight) / 2, listTop + 88, MUTED);
        }
        int settingsW = right - sx - 13;
        drawCard(g, sx, listTop, settingsW, listBottom - listTop, SURFACE);
        g.drawString(font, "SETTINGS", sx + 11, listTop + 11, ACCENT_BRIGHT, false);
        g.drawString(font, "Adjust Media Client", sx + 11, listTop + 25, MUTED, false);
        g.fill(sx + 9, listTop + 41, right - 22, listTop + 42, BORDER);
        int configX = sx + 10;
        switch (selected) {
            case ESP -> drawEspSettings(g, mouseX, mouseY, configX);
            case WORLD -> drawWorldSettings(g, mouseX, mouseY, configX);
            case PLAYER -> drawPlayerSettings(g, mouseX, mouseY, configX);
            case VISUAL -> drawVisualSettings(g, mouseX, mouseY, configX);
            case AUTOMATION -> drawAutomationSettings(g, configX);
            case COMBAT, PERFORMANCE, MISC -> drawCategoryInfo(g, configX, modules);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawEspSettings(GuiGraphics g, int mouseX, int mouseY, int x) {
        MediaConfig c = MediaConfig.get(); int y = settingsBaseY();
        settingLine(g, mouseX, mouseY, x, y, "Player range", c.playerRange + " blocks");
        settingLine(g, mouseX, mouseY, x, y + 20, "Container range", c.containerRange + " blocks");
        settingLine(g, mouseX, mouseY, x, y + 40, "Machine range", c.machineRange + " blocks");
        g.drawString(font, espFilterPage == 0 ? "MACHINE FILTERS" : "CONTAINER FILTERS", x, y + 62, ACCENT_BRIGHT, false);
        g.drawString(font, "Click to switch", x + 105, y + 62, MUTED, false);
        List<String> keys = new ArrayList<>((espFilterPage == 0 ? c.machineBlockToggles : c.containerBlockToggles).keySet());
        int startY = y + 81, rows = (keys.size() + 1) / 2, colWidth = 123;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i); int col = i / Math.max(1, rows), row = i % Math.max(1, rows);
            boolean checked = Boolean.TRUE.equals((espFilterPage == 0 ? c.machineBlockToggles : c.containerBlockToggles).get(key));
            drawTinyToggle(g, x + col * colWidth, startY + row * 15, ellipsize(titleCase(key.replace('_', ' ')), 92), checked);
        }
    }

    private void drawWorldSettings(GuiGraphics g, int mouseX, int mouseY, int x) {
        MediaConfig c = MediaConfig.get(); int y = settingsBaseY();
        settingLine(g, mouseX, mouseY, x, y, "Detection radius", c.activityRadius + " blocks");
        settingLine(g, mouseX, mouseY, x, y + 20, "Scan interval", c.scanIntervalTicks + " ticks");
        settingLine(g, mouseX, mouseY, x, y + 40, "Highlight time", c.highlightDurationSeconds + " sec");
        settingLine(g, mouseX, mouseY, x, y + 60, "Event limit", Integer.toString(c.maxTrackedEvents));
        g.drawString(font, "EVENT TYPES", x, y + 83, ACCENT_BRIGHT, false);
        g.drawString(font, "click to toggle", x + 92, y + 83, MUTED, false);
        List<String> keys = new ArrayList<>(c.eventToggles.keySet()); int startY = y + 101, rows = (keys.size() + 1) / 2, colWidth = 123;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            drawTinyToggle(g, x + (i / Math.max(1, rows)) * colWidth, startY + (i % Math.max(1, rows)) * 17,
                    ellipsize(titleCase(key.replace('_', ' ')), 95), Boolean.TRUE.equals(c.eventToggles.get(key)));
        }
    }

    private void drawPlayerSettings(GuiGraphics g, int mouseX, int mouseY, int x) {
        MediaConfig c = MediaConfig.get(); int y = settingsBaseY();
        g.drawString(font, "PLAYER ESP", x, y, ACCENT_BRIGHT, false);
        String[] labels = {"Show boxes","Show outlines","2D box style","Name tags","Distance","Health","Armor","Held items","Ignore invisible","Hide friends","Friend color","Hide teammates"};
        boolean[] values = {c.playerBox,c.playerOutline,c.playerEsp2D,c.playerName,c.playerDistance,c.playerHealth,c.playerArmor,c.playerHeldItems,c.ignoreInvisiblePlayers,c.hideFriends,c.useFriendColor,c.hideTeammates};
        int startY = y + 21;
        for (int i = 0; i < labels.length; i++) {
            boolean hover = mouseX >= x - 4 && mouseX <= panelX + PANEL_W - 22 && mouseY >= startY + i * 20 - 2 && mouseY <= startY + i * 20 + 14;
            if (hover) g.fill(x - 4, startY + i * 20 - 2, panelX + PANEL_W - 22, startY + i * 20 + 14, SURFACE_HOVER);
            g.drawString(font, labels[i], x, startY + i * 20, values[i] ? TEXT : MUTED, false);
            drawSwitch(g, panelX + PANEL_W - 53, startY + i * 20 - 1, values[i]);
        }
        g.drawString(font, "Friends: config/media-client.json", x, panelY + PANEL_H - 32, MUTED, false);
    }

    private void drawVisualSettings(GuiGraphics g, int mouseX, int mouseY, int x) {
        MediaConfig c = MediaConfig.get(); int y = settingsBaseY();
        g.drawString(font, "COLOR PALETTE", x, y, ACCENT_BRIGHT, false);
        g.drawString(font, "click to cycle", x, y + 15, MUTED, false);
        colorLine(g, mouseX, mouseY, x, y + 34, "Player ESP", c.playerColor);
        colorLine(g, mouseX, mouseY, x, y + 60, "Friend ESP", c.friendColor);
        colorLine(g, mouseX, mouseY, x, y + 86, "Container ESP", c.containerColor);
        colorLine(g, mouseX, mouseY, x, y + 112, "Machine ESP", c.machineColor);
        colorLine(g, mouseX, mouseY, x, y + 138, "Activity marks", c.activityColor);
    }

    private void drawAutomationSettings(GuiGraphics g, int x) {
        MediaConfig c = MediaConfig.get(); int y = settingsBaseY();
        settingLine(g, 0, 0, x, y, "Placement speed", c.blocksPerSecond + " blocks/s");
        settingLine(g, 0, 0, x, y + 20, "Placement delay", c.placementDelayMs + " ms");
        settingLine(g, 0, 0, x, y + 40, "Pause distance", c.builderPauseDistance + " blocks");
        g.drawString(font, "SCHEMATIC BUILDER", x, y + 72, ACCENT_BRIGHT, false);
        g.drawString(font, "Place .schem files in", x, y + 91, MUTED, false);
        g.drawString(font, ".minecraft/media-client/schematics", x, y + 104, MUTED, false);
        g.drawString(font, ellipsize(MediaClient.SCHEMATIC_BUILDER.status(), 236), x, y + 129, GREEN, false);
    }

    private void drawCategoryInfo(GuiGraphics g, int x, List<ClientModule> modules) {
        int y = settingsBaseY();
        if (modules.isEmpty()) {
            g.drawString(font, "NOT CONFIGURED", x, y, ACCENT_BRIGHT, false);
            g.drawWordWrap(font, Component.literal("This category has no registered modules yet. The panel will show controls here as real features are added."), x, y + 21, 230, MUTED);
        } else {
            g.drawString(font, "MODULE CONTROLS", x, y, ACCENT_BRIGHT, false);
            g.drawWordWrap(font, Component.literal("Enable a module from the left panel. Category-specific settings appear here when available."), x, y + 21, 230, MUTED);
        }
    }

    private void settingLine(GuiGraphics g, int mouseX, int mouseY, int x, int y, String label, String value) {
        boolean hover = mouseX >= x - 4 && mouseX <= panelX + PANEL_W - 20 && mouseY >= y - 3 && mouseY <= y + 14;
        if (hover) g.fill(x - 5, y - 4, panelX + PANEL_W - 21, y + 15, SURFACE_HOVER);
        g.drawString(font, label, x, y, TEXT, false);
        int valueX = panelX + PANEL_W - 23 - font.width(value);
        g.drawString(font, value, Math.max(x + 105, valueX), y, ACCENT_BRIGHT, false);
    }
    private void drawTinyToggle(GuiGraphics g, int x, int y, String label, boolean enabled) {
        g.fill(x, y + 1, x + 10, y + 11, enabled ? ACCENT : BORDER);
        if (enabled) g.fill(x + 3, y + 4, x + 7, y + 8, TEXT);
        g.drawString(font, label, x + 14, y + 2, enabled ? TEXT : MUTED, false);
    }
    private void colorLine(GuiGraphics g, int mouseX, int mouseY, int x, int y, String label, int color) {
        boolean hover = mouseX >= x - 4 && mouseX <= panelX + PANEL_W - 21 && mouseY >= y - 4 && mouseY <= y + 17;
        if (hover) g.fill(x - 4, y - 4, panelX + PANEL_W - 21, y + 17, SURFACE_HOVER);
        g.fill(x, y, x + 16, y + 16, color);
        g.drawString(font, label, x + 24, y + 4, TEXT, false);
        String hex = String.format("#%06X", color & 0xFFFFFF);
        g.drawString(font, hex, panelX + PANEL_W - 23 - font.width(hex), y + 4, MUTED, false);
    }
    private void drawSwitch(GuiGraphics g, int x, int y, boolean enabled) {
        g.fill(x, y, x + 30, y + 13, enabled ? ACCENT : 0xFF34394E);
        g.fill(x + 1, y + 1, x + 29, y + 12, enabled ? 0xFF7136C5 : 0xFF252A3B);
        int knobX = enabled ? x + 17 : x + 3;
        g.fill(knobX, y + 2, knobX + 9, y + 11, enabled ? TEXT : 0xFFABB3CD);
        if (enabled) g.fill(x - 1, y - 1, x + 31, y, ACCENT_BRIGHT);
    }
    private void drawCard(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + 5, y + 4, ACCENT);
    }
    private void drawLogo(GuiGraphics g, int x, int y, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, x, y, 0.0F, 0.0F, size, size, 256, 256);
    }
    private void drawAvatar(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 15, y + 15, ACCENT_WASH);
        g.fill(x + 5, y + 3, x + 10, y + 8, ACCENT_BRIGHT);
        g.fill(x + 3, y + 9, x + 12, y + 13, ACCENT);
    }
    private String categoryGlyph(ModuleCategory category) {
        return switch (category) { case COMBAT -> "C"; case PLAYER -> "P"; case VISUAL -> "V"; case WORLD -> "W"; case ESP -> "E"; case AUTOMATION -> "A"; case PERFORMANCE -> "F"; case MISC -> "M"; };
    }
    private String categorySubtitle(ModuleCategory category) {
        return switch (category) {
            case COMBAT -> "Combat options and module controls."; case PLAYER -> "Player appearance and filtering.";
            case VISUAL -> "Colors and on-screen presentation."; case WORLD -> "Detectable activity and world changes.";
            case ESP -> "Players, containers, machines and activity."; case AUTOMATION -> "Schematic loading and cautious placement.";
            case PERFORMANCE -> "Tune background work and scan cadence."; case MISC -> "Notifications and general preferences.";
        };
    }
    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String s = text; while (!s.isEmpty() && font.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
    private static String titleCase(String value) {
        StringBuilder out = new StringBuilder();
        for (String word : value.split(" ")) { if (word.isEmpty()) continue; if (!out.isEmpty()) out.append(' '); out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); }
        return out.toString();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        int x = (int) event.x(), y = (int) event.y(), closeLeft = panelX + PANEL_W - 34;
        if (x >= closeLeft && x <= panelX + PANEL_W - 8 && y >= panelY + 7 && y <= panelY + 34) { onClose(); return true; }
        int categoryY = panelY + 82;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (x >= panelX + 10 && x <= panelX + SIDEBAR_W - 11 && y >= categoryY - 4 && y <= categoryY + 23) {
                selected = category; clearWidgets(); init(); return true;
            }
            categoryY += 31;
        }
        int left = contentX() - 4, right = settingsX() - 9, rowY = panelY + 123;
        for (ClientModule module : MediaClient.MODULES.inCategory(selected)) {
            if (x >= left + 8 && x <= right - 8 && y >= rowY && y <= rowY + 55) {
                module.toggle();
                if (module.id().equals("schematic_builder") && !module.enabled()) MediaClient.SCHEMATIC_BUILDER.stop();
                return true;
            }
            rowY += 64;
        }
        MediaConfig c = MediaConfig.get(); int bx = settingsX() + 10, baseY = settingsBaseY();
        if (selected == ModuleCategory.ESP) {
            if (y >= baseY + 58 && y <= baseY + 75 && x >= bx) { espFilterPage = 1 - espFilterPage; return true; }
            if (clickSetting(x,y,bx,baseY)) c.playerRange = cycle(c.playerRange,new int[]{32,48,64,96,128});
            else if (clickSetting(x,y,bx,baseY+20)) c.containerRange = cycle(c.containerRange,new int[]{16,24,32,40,48});
            else if (clickSetting(x,y,bx,baseY+40)) c.machineRange = cycle(c.machineRange,new int[]{16,24,32,40,48});
            else {
                List<String> keys = new ArrayList<>((espFilterPage == 0 ? c.machineBlockToggles : c.containerBlockToggles).keySet());
                int rows=(keys.size()+1)/2, startY=baseY+81, col=x>=bx+123?1:0, row=Math.floorDiv(y-startY,15), index=col*rows+row;
                if (x>=bx-2 && x<=panelX+PANEL_W-20 && row>=0 && row<rows && index>=0 && index<keys.size()) {
                    Map<String,Boolean> map=espFilterPage==0?c.machineBlockToggles:c.containerBlockToggles; String key=keys.get(index); map.put(key,!Boolean.TRUE.equals(map.get(key)));
                }
            }
            c.save();
        } else if (selected == ModuleCategory.WORLD) {
            if (clickSetting(x,y,bx,baseY)) c.activityRadius=cycle(c.activityRadius,new int[]{4,6,8,10,12,16,20});
            else if (clickSetting(x,y,bx,baseY+20)) c.scanIntervalTicks=cycle(c.scanIntervalTicks,new int[]{5,10,20,30,40,60,100});
            else if (clickSetting(x,y,bx,baseY+40)) c.highlightDurationSeconds=cycle(c.highlightDurationSeconds,new int[]{2,3,5,8,12,20,30});
            else if (clickSetting(x,y,bx,baseY+60)) c.maxTrackedEvents=cycle(c.maxTrackedEvents,new int[]{50,100,200,300,500,1000,2000});
            else {
                List<String> keys=new ArrayList<>(c.eventToggles.keySet()); int rows=(keys.size()+1)/2,startY=baseY+101,col=x>=bx+123?1:0,row=Math.floorDiv(y-startY,17),index=col*rows+row;
                if (x>=bx-2&&x<=panelX+PANEL_W-20&&row>=0&&row<rows&&index>=0&&index<keys.size()) { String key=keys.get(index); c.eventToggles.put(key,!Boolean.TRUE.equals(c.eventToggles.get(key))); }
            }
            c.save();
        } else if (selected == ModuleCategory.PLAYER) {
            int row=Math.floorDiv(y-(baseY+21),20);
            if (x>=bx-4&&x<=panelX+PANEL_W-20&&row>=0&&row<12) {
                switch(row) {
                    case 0 -> c.playerBox=!c.playerBox; case 1 -> c.playerOutline=!c.playerOutline; case 2 -> c.playerEsp2D=!c.playerEsp2D;
                    case 3 -> c.playerName=!c.playerName; case 4 -> c.playerDistance=!c.playerDistance; case 5 -> c.playerHealth=!c.playerHealth;
                    case 6 -> c.playerArmor=!c.playerArmor; case 7 -> c.playerHeldItems=!c.playerHeldItems; case 8 -> c.ignoreInvisiblePlayers=!c.ignoreInvisiblePlayers;
                    case 9 -> c.hideFriends=!c.hideFriends; case 10 -> c.useFriendColor=!c.useFriendColor; case 11 -> c.hideTeammates=!c.hideTeammates;
                }
                c.save();
            }
        } else if (selected == ModuleCategory.VISUAL) {
            int[] colors={0xFF54D6FF,0xFFFFC85A,0xFFB28AFF,0xFFFF6B82,0xFF58E68A,TEXT,0xFF42A5F5};
            int[] slots={c.playerColor,c.friendColor,c.containerColor,c.machineColor,c.activityColor};
            int row=Math.floorDiv(y-(baseY+34),26);
            if (x>=bx-4&&x<=panelX+PANEL_W-20&&row>=0&&row<5) {
                int old=slots[row],next=colors[0];
                for(int i=0;i<colors.length;i++) if(colors[i]==old){next=colors[(i+1)%colors.length];break;}
                switch(row){case 0->c.playerColor=next;case 1->c.friendColor=next;case 2->c.containerColor=next;case 3->c.machineColor=next;case 4->c.activityColor=next;}
                c.save();
            }
        } else if (selected == ModuleCategory.AUTOMATION) {
            if (clickSetting(x,y,bx,baseY)) c.blocksPerSecond=cycle(c.blocksPerSecond,new int[]{1,2,4,6,8,10,12,16,20});
            else if (clickSetting(x,y,bx,baseY+20)) c.placementDelayMs=cycle(c.placementDelayMs,new int[]{0,50,100,120,200,300,500,750,1000});
            else if (clickSetting(x,y,bx,baseY+40)) c.builderPauseDistance=cycle(c.builderPauseDistance,new int[]{3,4,6,8,10,12,16,24,32});
            c.save();
        }
        return true;
    }

    private boolean clickSetting(int mouseX,int mouseY,int x,int y) { return mouseX>=x-4&&mouseX<=panelX+PANEL_W-20&&mouseY>=y-4&&mouseY<=y+15; }
    private static int cycle(int current,int[] values) { for(int i=0;i<values.length;i++) if(values[i]==current) return values[(i+1)%values.length]; return values[0]; }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { MediaConfig.get().save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
