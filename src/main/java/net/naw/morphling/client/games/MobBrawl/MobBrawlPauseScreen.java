package net.naw.morphling.client.games.MobBrawl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NonNull;

/**
 * Mob Brawl pause overlay — opened when player presses G during a fight.

 * Shows a simple dark overlay with two options:
 *   ▶ Resume    — close this screen, fight continues
 *   ✖ Forfeit   — concede the match, opponent wins

 * Red accent color matching Mob Brawl theme.
 * isPauseScreen returns false so the world keeps ticking.
 */
public class MobBrawlPauseScreen extends Screen {

    private static final int ACCENT = 0xFFFF5555;

    private float phaseTimer = 0f;
    private final float[] btnHover = new float[2];

    public MobBrawlPauseScreen() {
        super(Component.literal("Mob Brawl — Paused"));
    }

    @Override
    protected void init() {}

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        phaseTimer += dt;

        int cx = this.width / 2;

        // Dark dim
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        // Panel
        int panelW = 220, panelH = 100;
        int panelX = cx - panelW / 2;
        int panelY = this.height / 2 - panelH / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF0A0A18);
        graphics.fill(panelX,            panelY,            panelX + panelW, panelY + 1,       ACCENT);
        graphics.fill(panelX,            panelY + panelH-1, panelX + panelW, panelY + panelH,  ACCENT);
        graphics.fill(panelX,            panelY,            panelX + 1,      panelY + panelH,  ACCENT);
        graphics.fill(panelX + panelW-1, panelY,            panelX + panelW, panelY + panelH,  ACCENT);

        // Title
        float pulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        graphics.centeredText(this.font, Component.literal("⚔ Paused"),
                cx, panelY + 10, withAlpha(0xFF5555, (int)(180 + pulse * 75)));

        // Two buttons
        String[] labels = {"▶  Resume", "✖  Forfeit"};
        int[]    colors = {0xFF55FF55,  0xFFFF5555};
        int btnW = 160, btnH = 20, btnStartY = panelY + 28, btnGap = 6;

        for (int i = 0; i < 2; i++) {
            int btnX = cx - btnW / 2;
            int btnY = btnStartY + i * (btnH + btnGap);
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            btnHover[i] += ((hovered ? 1f : 0f) - btnHover[i]) * 0.2f;

            int bg     = withAlpha(colors[i] & 0x00FFFFFF, (int)(btnHover[i] * 40 + 10));
            int border = withAlpha(colors[i] & 0x00FFFFFF, (int)(btnHover[i] * 120 + 40));
            graphics.fill(btnX,            btnY,            btnX + btnW, btnY + btnH, bg);
            graphics.fill(btnX,            btnY,            btnX + btnW, btnY + 1,    border);
            graphics.fill(btnX,            btnY + btnH - 1, btnX + btnW, btnY + btnH, border);
            graphics.fill(btnX,            btnY,            btnX + 1,    btnY + btnH, border);
            graphics.fill(btnX + btnW - 1, btnY,            btnX + btnW, btnY + btnH, border);
            graphics.centeredText(this.font, Component.literal(labels[i]), cx, btnY + 6, colors[i]);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        int cx = this.width / 2;
        int btnW = 160, btnH = 20;
        int panelH    = 100;
        int panelY    = this.height / 2 - panelH / 2;
        int btnStartY = panelY + 28;
        int btnGap    = 6;

        for (int i = 0; i < 2; i++) {
            int btnX = cx - btnW / 2;
            int btnY = btnStartY + i * (btnH + btnGap);
            if (event.x() >= btnX && event.x() < btnX + btnW && event.y() >= btnY && event.y() < btnY + btnH) {
                switch (i) {
                    case 0 -> {
                        // Resume
                        playSound(1.2f);
                        Minecraft.getInstance().setScreen(null);
                    }
                    case 1 -> {
                        // Forfeit — opponent wins, notify server
                        playSound(0.8f);
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new MobBrawlNetworking.MobBrawlForfeitPayload(MobBrawlClient.getActiveRoomId())
                        );
                    }
                }
                return true;
            }
        }
        return true; // block clicks behind overlay
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    private void playSound(float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, pitch, false);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}