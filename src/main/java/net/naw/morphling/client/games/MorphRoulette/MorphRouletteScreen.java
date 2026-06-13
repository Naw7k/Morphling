package net.naw.morphling.client.games.MorphRoulette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.naw.morphling.client.games.ui.MorphGameModeSelect;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Morph Roulette pause/end screen — opened only when needed, never during free play.

 * The HUD (morph name, timer, score) is handled separately by MorphRouletteHud
 * which renders via a HUD event without blocking player input.

 * This screen opens in two cases:
 *   1. Player presses G (morph menu key) during roulette → pause overlay shown
 *   2. Game ends (duration ran out or stopped) → end screen shown

 * isPauseScreen returns false so the world keeps ticking.

 * Pause overlay options:
 *   ▶ Resume      — close this screen, game continues
 *   ■ Stop Game   — end the session, show end screen

 * End screen:
 *   - "ROULETTE OVER!" title with blue pulse
 *   - Score animates up (slot machine rollup over ~1.5s)
 *   - Rank badge fades in with a pling sound after score settles
 *   - Top 5 local leaderboard fades in after rank
 *   - ▶ Play Again — back to config screen
 *   - ← Back to Menu — back to mode select
 */
public class MorphRouletteScreen extends Screen {

    private final MorphRouletteGame game = MorphRouletteGame.getInstance();

    // ── Mode ──────────────────────────────────────────────────────────────────
    // PAUSE = escape overlay, END = final results screen
    public enum Mode { PAUSE, END }
    private final Mode mode;

    // ── Animation ─────────────────────────────────────────────────────────────
    private float phaseTimer = 0f;
    private float hue        = 0f;

    // ── Pause overlay hover state — 0=Resume, 1=Stop ─────────────────────────
    private final float[] pauseHover = new float[3];

    // ── End screen state ──────────────────────────────────────────────────────
    private final int   finalScore;
    private final int   finalSpins;
    private       float endTimer          = 0f;   // time since end screen opened
    @SuppressWarnings("FieldCanBeLocal")
    private       float scoreDisplay      = 0f;   // animated score rolling up
    private       boolean rankSoundPlayed = false;

    // End screen button hover
    private float playAgainHover = 0f;
    private float backHover      = 0f;

    /** Opens in pause mode — player pressed G mid-game */
    public MorphRouletteScreen() {
        super(Component.literal("Morph Roulette"));
        this.mode       = Mode.PAUSE;
        this.finalScore = game.getScore();
        this.finalSpins = game.getSpinCount();
    }

    /** Opens in end mode — game finished (duration ran out or manually stopped) */
    public MorphRouletteScreen(int finalScore, int finalSpins) {
        super(Component.literal("Morph Roulette — Results"));
        this.mode       = Mode.END;
        this.finalScore = finalScore;
        this.finalSpins = finalSpins;
    }

    @Override
    protected void init() {
        // No widgets — all interaction handled manually in mouseClicked/keyPressed
        // so we have full control over layout and hover animations
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        phaseTimer += dt;
        hue = (hue + dt * 0.3f) % 1.0f;

        if (mode == Mode.PAUSE) {
            renderPauseOverlay(graphics, mouseX, mouseY);
        } else {
            renderEndScreen(graphics, mouseX, mouseY, dt);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── Pause overlay ─────────────────────────────────────────────────────────

    private void renderPauseOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cx = this.width / 2;

        // Dark dim behind overlay
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        // Panel
        int panelW = 220, panelH = 100;
        int panelX = cx - panelW / 2;
        int panelY = this.height / 2 - panelH / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF0A0A18);
        graphics.fill(panelX,            panelY,            panelX + panelW, panelY + 1,       0xFF5599FF);
        graphics.fill(panelX,            panelY + panelH-1, panelX + panelW, panelY + panelH,  0xFF5599FF);
        graphics.fill(panelX,            panelY,            panelX + 1,      panelY + panelH,  0xFF5599FF);
        graphics.fill(panelX + panelW-1, panelY,            panelX + panelW, panelY + panelH,  0xFF5599FF);

        graphics.centeredText(this.font, Component.literal("§7Paused"), cx, panelY + 10, 0xFF333355);

        // Two buttons: Resume / Stop Game
        String[] labels = {"▶  Resume", "■  Stop Game"};
        int[]    colors = {0xFF55FF55,  0xFFFF5555};
        int btnW = 160, btnH = 20, btnStartY = panelY + 28, btnGap = 6;

        for (int i = 0; i < 2; i++) {
            int btnX = cx - btnW / 2;
            int btnY = btnStartY + i * (btnH + btnGap);
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            pauseHover[i] += ((hovered ? 1f : 0f) - pauseHover[i]) * 0.2f;

            int bg     = withAlpha(colors[i] & 0x00FFFFFF, (int)(pauseHover[i] * 40 + 10));
            int border = withAlpha(colors[i] & 0x00FFFFFF, (int)(pauseHover[i] * 120 + 40));
            graphics.fill(btnX,            btnY,            btnX + btnW, btnY + btnH, bg);
            graphics.fill(btnX,            btnY,            btnX + btnW, btnY + 1,    border);
            graphics.fill(btnX,            btnY + btnH - 1, btnX + btnW, btnY + btnH, border);
            graphics.fill(btnX,            btnY,            btnX + 1,    btnY + btnH, border);
            graphics.fill(btnX + btnW - 1, btnY,            btnX + btnW, btnY + btnH, border);
            graphics.centeredText(this.font, Component.literal(labels[i]), cx, btnY + 6, colors[i]);
        }
    }

    // ── End screen ────────────────────────────────────────────────────────────

    private void renderEndScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float dt) {
        endTimer += dt;
        int cx = this.width / 2;

        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        // ── Title ─────────────────────────────────────────────────────────────
        int   titleAlpha = (int)(Math.min(1f, endTimer / 0.5f) * 255);
        float titlePulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        graphics.centeredText(this.font, Component.literal("ROULETTE OVER!"),
                cx, 14, withAlpha(0x5599FF, (int)(titleAlpha * (0.7f + titlePulse * 0.3f))));

        // ── Animated score rollup — starts at 0.4s, takes ~1.5s ──────────────
        if (endTimer > 0.4f) {
            float rollProgress  = Math.min(1f, (endTimer - 0.4f) / 1.5f);
            scoreDisplay        = rollProgress * finalScore;
            int displayedScore  = (int)scoreDisplay;
            int scoreAlpha      = (int)(Math.min(1f, (endTimer - 0.4f) / 0.3f) * 255);

            graphics.centeredText(this.font, Component.literal("§7Score"),
                    cx, 30, withAlpha(0x888888, scoreAlpha));

            // Score pulses while rolling, static when done
            float scorePulse = rollProgress < 1f ? (float)(Math.sin(phaseTimer * 20) * 0.5 + 0.5) : 0f;
            int   scoreColor = rollProgress < 1f
                    ? withAlpha(0xFFFFFF, 200 + (int)(scorePulse * 55))
                    : 0xFFFFFFFF;
            graphics.centeredText(this.font, Component.literal("⭐ " + displayedScore),
                    cx, 42, scoreColor);

            graphics.centeredText(this.font, Component.literal("§7Spins: §f" + finalSpins),
                    cx, 56, withAlpha(0x888888, scoreAlpha));
        }

        // ── Rank reveal — appears after score settles at ~2.0s ────────────────
        if (endTimer > 2.0f) {
            float rankAlpha = Math.min(1f, (endTimer - 2.0f) / 0.4f);

            // Play rank pling once
            if (!rankSoundPlayed) {
                rankSoundPlayed = true;
                playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f);
            }

            float rankPulse = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
            int   rankRgb   = java.awt.Color.HSBtoRGB(0.15f + rankPulse * 0.05f, 1f, 1f);
            graphics.centeredText(this.font,
                    Component.literal("✦ " + getRank(finalScore) + " ✦"),
                    cx, 70, withAlpha(rankRgb & 0x00FFFFFF, (int)(rankAlpha * 220)));
        }

        // ── Local leaderboard — top 5 runs, fades in at 2.5s ─────────────────
        if (endTimer > 2.5f) {
            float lbAlpha = Math.min(1f, (endTimer - 2.5f) / 0.6f);
            int   lbAlphaI = (int)(lbAlpha * 255);

            List<MorphRouletteStats.RunEntry> runs = MorphRouletteStats.get().topRuns;

            int lbX     = cx - 130;
            int lbY     = 88;
            int lbW     = 260;
            int rowH    = 14;

            // Header
            graphics.fill(lbX, lbY, lbX + lbW, lbY + 1, withAlpha(0x5599FF, (int)(lbAlpha * 60)));
            graphics.centeredText(this.font, Component.literal("§8🏆 Best Runs"),
                    cx, lbY + 3, withAlpha(0x334466, lbAlphaI));
            lbY += 14;

            if (runs.isEmpty()) {
                graphics.centeredText(this.font, Component.literal("§8No runs yet"),
                        cx, lbY, withAlpha(0x333344, lbAlphaI));
            } else {
                for (int r = 0; r < Math.min(runs.size(), 5); r++) {
                    MorphRouletteStats.RunEntry run = runs.get(r);

                    // Highlight current run
                    boolean isCurrentRun = run.score == finalScore && run.spins == finalSpins;
                    int rowBg = isCurrentRun ? withAlpha(0x5599FF, (int)(lbAlpha * 20)) : 0;
                    if (rowBg != 0) graphics.fill(lbX, lbY - 1, lbX + lbW, lbY + rowH, rowBg);

                    // Rank medal
                    String medal = switch (r) {
                        case 0 -> "§6#1";
                        case 1 -> "§7#2";
                        case 2 -> "§c#3";
                        default -> "§8#" + (r + 1);
                    };

                    int rowColor = isCurrentRun ? withAlpha(0xFFFFFF, lbAlphaI) : withAlpha(0x888888, lbAlphaI);
                    int scoreColor = isCurrentRun ? withAlpha(0xFFAA00, lbAlphaI) : withAlpha(0x888866, lbAlphaI);

                    graphics.text(this.font, Component.literal(medal),
                            lbX + 4, lbY, rowColor, false);
                    graphics.text(this.font, Component.literal("⭐ " + run.score),
                            lbX + 28, lbY, scoreColor, false);
                    graphics.text(this.font, Component.literal("§8" + run.configLabel),
                            lbX + 60, lbY, withAlpha(0x444455, lbAlphaI), false);
                    graphics.text(this.font, Component.literal("§8" + run.date),
                            lbX + lbW - this.font.width(run.date) - 4, lbY, withAlpha(0x333344, lbAlphaI), false);

                    lbY += rowH;
                }
            }
        }

        // ── Buttons appear after leaderboard at ~3.0s ─────────────────────────
        if (endTimer > 3.0f) {
            float btnAlpha = Math.min(1f, (endTimer - 3.0f) / 0.4f);
            int   btnW     = 130;
            int   btnX     = cx - btnW / 2;
            int   btnY1    = this.height - 50;
            int   btnY2    = this.height - 28;

            // Play Again
            boolean paHov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY1 && mouseY < btnY1 + 20;
            playAgainHover += ((paHov ? 1f : 0f) - playAgainHover) * 0.2f;
            renderEndBtn(graphics, btnX, btnY1, btnW, "▶  Play Again",   0xFF55FF55, playAgainHover, btnAlpha);

            // Back to Menu
            boolean bmHov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY2 && mouseY < btnY2 + 20;
            backHover += ((bmHov ? 1f : 0f) - backHover) * 0.2f;
            renderEndBtn(graphics, btnX, btnY2, btnW, "← Back to Menu", 0xFF5599FF, backHover, btnAlpha);
        }
    }

    /** Renders a styled end screen button with hover glow and fade-in alpha */
    private void renderEndBtn(GuiGraphicsExtractor graphics, int x, int y, int w,
                              String label, int color, float hover, float alpha) {
        int bg     = withAlpha(color & 0x00FFFFFF, (int)((hover * 40 + 10) * alpha));
        int border = withAlpha(color & 0x00FFFFFF, (int)((hover * 120 + 60) * alpha));
        graphics.fill(x,       y,       x + w, y + 20, bg);
        graphics.fill(x,       y,       x + w, y + 1,   border);
        graphics.fill(x,       y + 20-1, x + w, y + 20,  border);
        graphics.fill(x,       y,       x + 1, y + 20,   border);
        graphics.fill(x + w-1, y,       x + w, y + 20,   border);
        graphics.centeredText(this.font, Component.literal(label), x + w / 2, y + 6, withAlpha(color, (int)(alpha * 255)));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && mode == Mode.PAUSE) {
            // Escape on pause overlay = resume
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        int cx = this.width / 2;

        // ── Pause overlay clicks ──────────────────────────────────────────────
        if (mode == Mode.PAUSE) {
            int btnW      = 160, btnH = 20;
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
                            // Resume — close this screen, game keeps running via HUD
                            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
                            Minecraft.getInstance().setScreen(null);
                        }
                        case 1 -> {
                            // Stop Game → record session and show end screen
                            playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f);
                            int score = game.getScore();
                            int spins = game.getSpinCount();
                            float elapsed = game.getElapsed();
                            game.stop();
                            MorphRouletteStats.recordSession(score, spins, elapsed, game.getConfigLabel());
                            playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.8f);
                            Minecraft.getInstance().setScreen(new MorphRouletteScreen(score, spins));
                        }
                    }
                    return true;
                }
            }
            return true; // block clicks behind pause overlay
        }

        // ── End screen clicks ─────────────────────────────────────────────────
        if (mode == Mode.END && endTimer > 3.0f) {
            int btnW  = 130;
            int btnX  = cx - btnW / 2;
            int btnY1 = this.height - 50;
            int btnY2 = this.height - 28;

            if (event.x() >= btnX && event.x() < btnX + btnW && event.y() >= btnY1 && event.y() < btnY1 + 20) {
                // Play Again — back to config
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
                Minecraft.getInstance().setScreen(new MorphRouletteConfigScreen());
                return true;
            }
            if (event.x() >= btnX && event.x() < btnX + btnW && event.y() >= btnY2 && event.y() < btnY2 + 20) {
                // Back to mode select
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.ROULETTE));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getRank(int score) {
        if (score >= 50) return "S — Roulette Master";
        if (score >= 30) return "A — Morph Veteran";
        if (score >= 15) return "B — Getting Comfortable";
        if (score >= 5)  return "C — Still Adapting";
        return                  "D — Keep Spinning";
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    sound, SoundSource.PLAYERS, 0.6f, pitch, false);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; } // world keeps ticking

    @Override
    public void onClose() {
        // Only stop game if we're closing the end screen — not the pause overlay
        if (mode == Mode.END && game.isRunning()) game.stop();
        super.onClose();
    }
}