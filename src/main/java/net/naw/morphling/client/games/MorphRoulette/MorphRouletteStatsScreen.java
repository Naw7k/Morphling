package net.naw.morphling.client.games.MorphRoulette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.naw.morphling.client.games.ui.MorphGameModeSelect;
import org.jspecify.annotations.NonNull;

/**
 * Morph Roulette stats screen — shows all-time persistent stats.

 * Opened from the Stats button on MorphGameModeSelect (Roulette mode).

 * Layout:
 *   - Animated title at top with roulette blue pulse
 *   - 6 stat cards arranged in a 2x3 grid, each with an icon, value, and label
 *   - Subtle animated background lines
 *   - Back button at bottom

 * Stats shown:
 *   🏆 Best Score     — most morphs survived in one session
 *   🔄 Total Spins    — all-time spins across all sessions
 *   ✅ Total Survived  — all-time morphs survived
 *   🎮 Sessions       — total sessions played
 *   ⏱ Time Played    — total time played formatted
 *   🌟 Longest Session — most spins in a single session
 */
public class MorphRouletteStatsScreen extends Screen {

    // ── Animation ─────────────────────────────────────────────────────────────
    private float phaseTimer = 0f;
    private float hue        = 0f;
    private float introAlpha = 0f;

    // ── Card hover state ──────────────────────────────────────────────────────
    private final float[] cardHover = new float[6];

    // ── Stat definitions ──────────────────────────────────────────────────────
    private static final String[] ICONS   = {"🏆", "🔄", "✅", "🎮", "⏱", "🌟"};
    private static final String[] LABELS  = {"Best Score", "Total Spins", "Total Score", "Sessions Played", "Time Played", "Longest Session"};
    private static final int[]    ACCENTS = {
            0xFFFFAA00, // gold — best score
            0xFF5599FF, // blue — total spins
            0xFF55FF55, // green — total survived
            0xFFCC55FF, // purple — sessions
            0xFF55FFCC, // teal — time played
            0xFFFF5555, // red — longest session
    };

    public MorphRouletteStatsScreen() {
        super(Component.literal("Morph Roulette — Stats"));
    }

    @Override
    protected void init() {
        MorphRouletteStats.load();

        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> {
                    this.onClose();
                    Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.ROULETTE));
                }
        ).bounds(this.width / 2 - 55, this.height - 30, 110, 20).build());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        phaseTimer += dt;
        hue        = (hue + dt * 0.2f) % 1.0f;
        introAlpha = Math.min(1f, introAlpha + dt * 2f);

        int alpha = (int)(introAlpha * 255);
        int cx    = this.width / 2;

        // ── Background ────────────────────────────────────────────────────────
        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        // Subtle animated scan lines
        for (int i = 0; i < 3; i++) {
            float prog  = ((phaseTimer * 0.15f + i * 0.33f) % 1.0f);
            int   la    = (int)(Math.sin(prog * Math.PI) * 3);
            int   lx    = (int)(prog * this.width * 1.5f) - this.width / 4;
            graphics.fill(lx, 0, lx + 80, this.height, (la << 24) | 0x5599FF);
        }

        // ── Title ─────────────────────────────────────────────────────────────
        float titlePulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        int   titleColor = withAlpha(0x5599FF, (int)((0.7f + titlePulse * 0.3f) * alpha));
        graphics.centeredText(this.font, Component.literal("🎲 Morph Roulette"), cx, 16, titleColor);
        graphics.centeredText(this.font, Component.literal("§7All-Time Stats"), cx, 28, withAlpha(0x444466, alpha));
        graphics.fill(cx - 100, 38, cx + 100, 39, withAlpha(0x5599FF, (int)(introAlpha * 50)));

        // ── Stat cards ────────────────────────────────────────────────────────
        MorphRouletteStats.Data d = MorphRouletteStats.get();
        String[] values = {
                String.valueOf(d.bestScore),
                String.valueOf(d.totalSpins),
                String.valueOf(d.totalMorphsSurvived),
                String.valueOf(d.totalSessions),
                MorphRouletteStats.formatTime(d.totalTimePlayed),
                d.longestSession + " spins",
        };

        int cols    = 3;
        int cardW   = Math.min(140, (this.width - 60) / cols);
        int cardH   = 70;
        int gapX    = 12;
        int gapY    = 12;
        int gridW   = cols * cardW + (cols - 1) * gapX;
        int startX  = cx - gridW / 2;
        int startY  = 50;

        for (int i = 0; i < 6; i++) {
            int col  = i % cols;
            int row  = i / cols;
            int cardX = startX + col * (cardW + gapX);
            int cardY = startY + row * (cardH + gapY);
            int accent = ACCENTS[i];

            boolean hovered = mouseX >= cardX && mouseX < cardX + cardW
                    && mouseY >= cardY && mouseY < cardY + cardH;
            cardHover[i] += ((hovered ? 1f : 0f) - cardHover[i]) * 0.15f;
            float h = cardHover[i];

            // Card background
            int bg = lerpColor(h);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, withAlpha(bg & 0x00FFFFFF, alpha));

            // Border — accent color pulses on hover
            float borderPulse = hovered ? (float)(Math.sin(phaseTimer * 4) * 0.5 + 0.5) : 0f;
            int   borderAlpha = (int)((h * 0.6f + 0.2f + borderPulse * 0.2f) * alpha);
            int   border      = withAlpha(accent & 0x00FFFFFF, borderAlpha);
            graphics.fill(cardX,            cardY,            cardX + cardW, cardY + 1,       border);
            graphics.fill(cardX,            cardY + cardH - 1, cardX + cardW, cardY + cardH,  border);
            graphics.fill(cardX,            cardY,            cardX + 1,     cardY + cardH,   border);
            graphics.fill(cardX + cardW - 1, cardY,           cardX + cardW, cardY + cardH,   border);

            // Inner glow on hover
            if (h > 0.05f) {
                graphics.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + 2,
                        withAlpha(accent & 0x00FFFFFF, (int)(h * 40)));
            }

            // Accent bar at top of card
            graphics.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + 3,
                    withAlpha(accent & 0x00FFFFFF, (int)(introAlpha * 60)));

            // Icon
            int iconAlpha = (int)(introAlpha * 220);
            graphics.centeredText(this.font, Component.literal(ICONS[i]),
                    cardX + cardW / 2, cardY + 10, withAlpha(accent & 0x00FFFFFF, iconAlpha));

            // Value — large, accent colored
            float valuePulse = hovered ? (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5) : 0.5f;
            int   valueColor = withAlpha(accent & 0x00FFFFFF, (int)((0.7f + valuePulse * 0.3f) * alpha));

            // Scale down font for long values
            String val = values[i];
            graphics.centeredText(this.font, Component.literal(val), cardX + cardW / 2, cardY + 30, valueColor);

            // Label — subtle grey
            graphics.centeredText(this.font, Component.literal("§8" + LABELS[i]),
                    cardX + cardW / 2, cardY + 46, withAlpha(0x444455, alpha));

            // "PB!" badge on best score card
            if (i == 0 && d.bestScore > 0) {
                float pbPulse = (float)(Math.sin(phaseTimer * 4) * 0.5 + 0.5);
                int   pbColor = withAlpha(0xFFAA00, (int)((0.6f + pbPulse * 0.4f) * alpha));
                graphics.centeredText(this.font, Component.literal("✦ Personal Best ✦"),
                        cardX + cardW / 2, cardY + 58, pbColor);
            }
        }



        // ── Rank based on best score ──────────────────────────────────────────
        if (d.bestScore > 0) {
            float rankPulse = (float)(Math.sin(phaseTimer * 1.5f) * 0.5 + 0.5);
            int   rankRgb   = java.awt.Color.HSBtoRGB(0.12f + rankPulse * 0.06f, 1f, 1f);
            graphics.centeredText(this.font,
                    Component.literal("Current Rank: §f" + getRank(d.bestScore)),
                    cx, startY + 2 * (cardH + gapY) + 8, withAlpha(rankRgb & 0x00FFFFFF, alpha));
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getRank(int bestScore) {
        if (bestScore >= 20) return "S — Roulette Master";
        if (bestScore >= 14) return "A — Morph Veteran";
        if (bestScore >= 8)  return "B — Getting Comfortable";
        if (bestScore >= 4)  return "C — Still Adapting";
        return                      "D — Keep Spinning";
    }

    private static int lerpColor(float t) {
        int ar = (-16119272 >> 16) & 0xFF, ag = (-16119272 >> 8) & 0xFF, ab = -16119272 & 0xFF;
        int br = (-15592920 >> 16) & 0xFF, bg = (-15592920 >> 8) & 0xFF, bb = -15592920 & 0xFF;
        return 0xFF000000
                | ((int)(ar + (br - ar) * t) << 16)
                | ((int)(ag + (bg - ag) * t) << 8)
                |  (int)(ab + (bb - ab) * t);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}