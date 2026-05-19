package net.naw.morphling.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class DebugScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 290;

    public DebugScreen() {
        super(Component.literal("Debug Panel"));
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int bx = px + 10;
        int bw = PANEL_W - 20;
        int y = py + 48;

        // ── Combat ──────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal("⚔ Damage Indicator: " + (DebugSettings.isDamageIndicatorEnabled() ? "ON" : "OFF")),
                btn -> {
                    DebugSettings.setDamageIndicatorEnabled(!DebugSettings.isDamageIndicatorEnabled());
                    btn.setMessage(Component.literal("⚔ Damage Indicator: " + (DebugSettings.isDamageIndicatorEnabled() ? "ON" : "OFF")));
                }
        ).bounds(bx, y, bw, 20).build());
        y += 34;

        // ── Movement ─────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal("💨 Speedometer: " + (DebugSettings.isSpeedometerEnabled() ? "ON" : "OFF")),
                btn -> {
                    DebugSettings.setSpeedometerEnabled(!DebugSettings.isSpeedometerEnabled());
                    btn.setMessage(Component.literal("💨 Speedometer: " + (DebugSettings.isSpeedometerEnabled() ? "ON" : "OFF")));
                }
        ).bounds(bx, y, bw, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(
                Component.literal("🏃 AI Mob Speed Test: " + (DebugSettings.isTestSpeedEnabled() ? "ON" : "OFF")),
                btn -> {
                    DebugSettings.setTestSpeedEnabled(!DebugSettings.isTestSpeedEnabled());
                    btn.setMessage(Component.literal("🏃 AI Mob Speed Test: " + (DebugSettings.isTestSpeedEnabled() ? "ON" : "OFF")));
                }
        ).bounds(bx, y, bw, 20).build());
        y += 34;

        // ── Visual ───────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal("🖐 Hand Placement Tuner"),
                _ -> Minecraft.getInstance().setScreen(new HandPlacementDebugScreen())
        ).bounds(bx, y, bw, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(
                Component.literal("🗡 Held Item Tuner"),
                _ -> Minecraft.getInstance().setScreen(new HeldItemTunerScreen())
        ).bounds(bx, y, bw, 20).build());
        y += 34;

        // ── Audio ────────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal("🔊 SoundConfig"),
                _ -> Minecraft.getInstance().setScreen(new SoundConfig(this))
        ).bounds(bx, y, bw, 20).build());
        y += 60;

        // ── Back & Close ─────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> Minecraft.getInstance().setScreen(new net.naw.morphling.client.ui.MorphMenuScreen())
        ).bounds(bx, y, bw / 2 - 2, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("× Close"),
                _ -> this.onClose()
        ).bounds(bx + bw / 2 + 2, y, bw / 2 - 2, 20).build());
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        // Dark panel
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xAA050505);
        // Red border
        graphics.fill(px, py, px + PANEL_W, py + 1, 0xFFCC0000);
        graphics.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, 0xAA050505);
        graphics.fill(px, py, px + 1, py + PANEL_H, 0xAA050505);
        graphics.fill(px + PANEL_W - 1, py, px + PANEL_W, py + PANEL_H, 0xAA050505);
        // Red title bar
        graphics.fill(px + 1, py + 1, px + PANEL_W - 1, py + 22, 0xFFCC0000);
    }

    @SuppressWarnings("ExtractMethodRecommender")
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int bx = px + 10;
        int bw = PANEL_W - 20;

        boolean isDedicated = net.minecraft.client.Minecraft.getInstance().getCurrentServer() != null;
        String serverStatus = (isDedicated && net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) ? "§aServer: ✔" : "§cServer: ✘";

        boolean isLan = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer() != null
                && net.minecraft.client.Minecraft.getInstance().getSingleplayerServer().isPublished();
        boolean isLanGuest = net.minecraft.client.Minecraft.getInstance().getCurrentServer() == null
                && !net.minecraft.client.Minecraft.getInstance().isLocalServer();
        String lanStatus = ((isLan || isLanGuest) && net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) ? "§aLAN: ✔" : "§cLAN: ✘";

        String morphStatus = net.naw.morphling.client.core.MorphState.isMorphed() ? "§aMorphed: ✔" : "§cMorphed: ✘";
        graphics.text(this.font, Component.literal(morphStatus), bx, py + PANEL_H - 67, -1, false);
        graphics.text(this.font, Component.literal(serverStatus), bx, py + PANEL_H - 54, -1, false);
        graphics.text(this.font, Component.literal(lanStatus), bx, py + PANEL_H - 42, -1, false);



        // Section dividers
        graphics.fill(bx, py + 46, bx + bw, py + 45, 0x88AAAAAA);
        graphics.fill(bx, py + 71, bx + bw, py + 70, 0x88AAAAAA);
        graphics.fill(bx, py + 129, bx + bw, py + 128, 0x88AAAAAA);
        graphics.fill(bx, py + 185, bx + bw, py + 186, 0x88AAAAAA);

        // Title
        graphics.centeredText(this.font, Component.literal("[ DEBUG PANEL ]"), this.width / 2, py + 7, -1);

        // Section labels
        graphics.text(this.font, Component.literal("COMBAT"), bx, py + 35, 0xFFAAAAAA, false);
        graphics.text(this.font, Component.literal("MOVEMENT"), bx, py + 73, 0xFFAAAAAA, false);
        graphics.text(this.font, Component.literal("VISUAL"), bx, py + 131, 0xFFAAAAAA, false);
        graphics.text(this.font, Component.literal("AUDIO"), bx, py + 188, 0xFFAAAAAA, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}