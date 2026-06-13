package net.naw.morphling.client.games.MorphRoulette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.naw.morphling.client.games.ui.MorphGameModeSelect;
import org.jspecify.annotations.NonNull;

/**
 * Pre-game config screen for Morph Roulette (single player).

 * Shown after clicking Solo on the Roulette mode select screen.
 * Player picks their settings, then hits Start — triggers a 3s countdown
 * before opening MorphRouletteScreen and starting the game.

 * Options:
 *   Duration   — how long the game lasts (5 / 10 / 20 min / Endless)
 *   Spin Speed — how often the morph changes (Slow 60s / Normal 30s / Fast 15s / Chaos 8s)
 *   Morph Pool — which morphs are in the rotation (All / Passive / Hostile / Random Mix)

 * Defaults: 10 min / Normal / All Morphs
 * Default options are marked with a subtle §8(default) tag.

 * After Start: 3s countdown then game begins.

 * Layout is fully dynamic — rowH and startY are calculated from available screen height
 * so the screen works at any GUI scale without overlapping the Start button.
 */
public class MorphRouletteConfigScreen extends Screen {

    // ── Option definitions ────────────────────────────────────────────────────

    private static final String[] DURATION_LABELS  = {"5 min", "10 min", "20 min", "Endless"};
    private static final int[]    DURATION_SECONDS  = {300,     600,      1200,     -1};
    private static final int      DURATION_DEFAULT  = 1; // 10 min

    private static final String[] SPEED_LABELS     = {"Slow", "Normal", "Fast", "Chaos"};
    private static final float[]  SPEED_SECONDS     = {60f,    30f,      15f,    8f};
    private static final int      SPEED_DEFAULT     = 1; // Normal

    private static final String[] POOL_LABELS       = {"All Morphs", "Passive Only", "Hostile Only", "Random Mix"};
    private static final int      POOL_DEFAULT       = 0; // All Morphs

    // ── Selection state ───────────────────────────────────────────────────────
    private int durationIndex = DURATION_DEFAULT;
    private int speedIndex    = SPEED_DEFAULT;
    private int poolIndex     = POOL_DEFAULT;

    // ── Countdown state ───────────────────────────────────────────────────────
    private boolean countingDown   = false;
    private float   countdownTimer = 3f;

    // ── Animation ─────────────────────────────────────────────────────────────
    private float phaseTimer = 0f;
    private float hue        = 0f;
    private float introAlpha = 0f;

    // Hover pulse per option button — [row][col]
    private final float[][] optionHover = new float[3][4];

    public MorphRouletteConfigScreen() {
        super(Component.literal("Morph Roulette — Setup"));
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /**
     * Dynamic row height — scales down on small screens / high GUI scale.
     * Reserves space for title (50px), summary (20px), hint (20px), button (40px).
     * Remaining height is split across 3 rows with 8px gaps between them.
     */
    private int getRowH() {
        int available = this.height - 50 - 20 - 20 - 40;
        return Math.clamp((available - 16) / 3, 28, 44);
    }

    /** Top Y of the first option row */
    private int getStartY() {
        return 52;
    }

    @Override
    protected void init() {
        // Back button — top left
        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> {
                    this.onClose();
                    Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.ROULETTE));
                }
        ).bounds(10, 10, 60, 20).build());

        // Start button — centered at bottom, position recalculated on each init() so it
        // never overlaps the rows regardless of GUI scale
        this.addRenderableWidget(Button.builder(
                Component.literal("▶  Start Game"),
                _ -> beginCountdown()
        ).bounds(this.width / 2 - 55, this.height - 28, 110, 22).build());
    }

    private void beginCountdown() {
        countingDown   = true;
        countdownTimer = 3f;
        clearWidgets(); // hide buttons during countdown
        playSound(1.0f);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        phaseTimer += dt;
        hue        = (hue + dt * 0.3f) % 1.0f;
        introAlpha = Math.min(1f, introAlpha + dt * 2f);

        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        if (countingDown) {
            renderCountdown(graphics, dt);
        } else {
            renderConfig(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── Config screen ─────────────────────────────────────────────────────────

    private void renderConfig(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cx     = this.width / 2;
        int alpha  = (int)(introAlpha * 255);
        int rowH   = getRowH();
        int startY = getStartY();

        // Title
        float pulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        int titleColor = withAlpha(0x5599FF, (int)(180 + pulse * 75));
        graphics.centeredText(this.font, Component.literal("🎲 Morph Roulette"), cx, 14, titleColor);
        graphics.centeredText(this.font, Component.literal("§7Configure your game"), cx, 26, withAlpha(0x666666, alpha));
        graphics.fill(cx - 120, 38, cx + 120, 39, withAlpha(0x5599FF, (int)(introAlpha * 60)));

        // ── Option rows ───────────────────────────────────────────────────────
        int panelW = Math.min(460, this.width - 40);
        int panelX = cx - panelW / 2;
        int gap    = 8;

        renderOptionRow(graphics, mouseX, mouseY, 0, panelX, startY,                    panelW, rowH, "⏱ Duration",   DURATION_LABELS, durationIndex, DURATION_DEFAULT, 0xFF5599FF);
        renderOptionRow(graphics, mouseX, mouseY, 1, panelX, startY + (rowH + gap),     panelW, rowH, "⚡ Spin Speed", SPEED_LABELS,    speedIndex,    SPEED_DEFAULT,    0xFFCC55FF);
        renderOptionRow(graphics, mouseX, mouseY, 2, panelX, startY + (rowH + gap) * 2, panelW, rowH, "🧬 Morph Pool", POOL_LABELS,     poolIndex,     POOL_DEFAULT,     0xFF55FFCC);

        // ── Summary line ──────────────────────────────────────────────────────
        int summaryY = startY + (rowH + gap) * 3 + 4;
        graphics.centeredText(this.font, Component.literal("§8" + buildSummary()), cx, summaryY, withAlpha(0x444455, alpha));

        // ── Start hint ────────────────────────────────────────────────────────
        float blink = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
        graphics.centeredText(this.font, Component.literal("§7Press Start when ready"),
                cx, this.height - 38, withAlpha(0x555566, (int)(blink * 180)));

        // ── Point system hint — bottom left corner ────────────────────────────
        graphics.text(this.font, Component.literal("§8🎲 Kill mobs to earn points"), 6, this.height - 46, withAlpha(0x333344, alpha));
        graphics.text(this.font, Component.literal("§8Passive — 1-2 pts"), 6, this.height - 36, withAlpha(0x333344, alpha));
        graphics.text(this.font, Component.literal("§8Hostile — 3 pts"), 6, this.height - 26, withAlpha(0x333344, alpha));
        graphics.text(this.font, Component.literal("§8Tough — 5 pts"), 6, this.height - 16, withAlpha(0x333344, alpha));
    }

    /**
     * Renders one option row: label on left, option buttons on right.
     * Selected option highlighted in accent color.
     * Default option shows §8(default) tag below label.
     */
    private void renderOptionRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                 int rowIndex, int x, int y, int w, int h,
                                 String label, String[] options, int selected, int defaultIdx, int accent) {
        // Row background
        graphics.fill(x, y, x + w, y + h, 0xFF0D0D1A);
        graphics.fill(x, y,       x + w, y + 1,   withAlpha(accent & 0x00FFFFFF, 40));
        graphics.fill(x, y + h-1, x + w, y + h,   withAlpha(accent & 0x00FFFFFF, 20));

        // Row label — vertically centered
        graphics.text(this.font, Component.literal(label), x + 10, y + h / 2 - 4, withAlpha(accent & 0x00FFFFFF, 200), false);

        // Option buttons
        int btnAreaW  = w - 130;
        int btnW      = (btnAreaW - (options.length - 1) * 4) / options.length;
        int btnStartX = x + w - btnAreaW - 10;

        for (int i = 0; i < options.length; i++) {
            int btnX = btnStartX + i * (btnW + 4);
            int btnY = y + 6;
            int btnH = h - 12;

            boolean isSelected = i == selected;
            boolean isDefault  = i == defaultIdx;
            boolean hovered    = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            // Smooth hover lerp
            float hoverT = optionHover[rowIndex][i];
            optionHover[rowIndex][i] += ((hovered ? 1f : 0f) - hoverT) * 0.2f;
            hoverT = optionHover[rowIndex][i];

            int bg     = isSelected ? withAlpha(accent & 0x00FFFFFF, 60) : withAlpha(0x111122, (int)(hoverT * 80 + 30));
            int border = isSelected ? accent : withAlpha(accent & 0x00FFFFFF, (int)(hoverT * 80 + 20));

            graphics.fill(btnX,            btnY,            btnX + btnW, btnY + btnH, bg);
            graphics.fill(btnX,            btnY,            btnX + btnW, btnY + 1,    border);
            graphics.fill(btnX,            btnY + btnH - 1, btnX + btnW, btnY + btnH, border);
            graphics.fill(btnX,            btnY,            btnX + 1,    btnY + btnH, border);
            graphics.fill(btnX + btnW - 1, btnY,            btnX + btnW, btnY + btnH, border);

            // Option label — centered in button
            int textColor = isSelected ? accent : withAlpha(0xCCCCCC, (int)(hoverT * 80 + 120));

            //noinspection IfStatementWithIdenticalBranches
            if (isDefault) {
                // Default: label slightly above center, "default" tag below
                graphics.centeredText(this.font, Component.literal(options[i]), btnX + btnW / 2, btnY + btnH / 2 - 4, textColor);

            } else {
                // Non-default: label fully centered
                graphics.centeredText(this.font, Component.literal(options[i]), btnX + btnW / 2, btnY + btnH / 2 - 4, textColor);
            }
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void renderCountdown(GuiGraphicsExtractor graphics, float dt) {
        countdownTimer -= dt;
        int num = (int)Math.ceil(countdownTimer);

        if (countdownTimer <= 0f) {
            // Apply config and launch game
            MorphRouletteGame game = MorphRouletteGame.getInstance();
            game.setConfig(SPEED_SECONDS[speedIndex], DURATION_SECONDS[durationIndex], poolIndex);
            game.start();
            Minecraft.getInstance().setScreen(null);
            return;
        }

        int   cx    = this.width / 2;
        float frac  = countdownTimer % 1.0f;
        float pulse = (float)(Math.sin((1f - frac) * Math.PI) * 0.5 + 0.5);
        int   alpha = 180 + (int)(pulse * 75);

        // Play tick sound once per second
        if (frac > 1f - dt * 2f || countdownTimer >= 3f - dt) {
            float pitch = num == 1 ? 2.0f : 1.0f + (3 - num) * 0.25f;
            playSound(pitch);
        }

        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        int countColor = num == 1 ? 0xFF55FF55 : withAlpha(rgb & 0x00FFFFFF, alpha);
        graphics.centeredText(this.font, Component.literal(String.valueOf(num)), cx, this.height / 2 - 20, countColor);
        graphics.centeredText(this.font, Component.literal("§7Get ready..."), cx, this.height / 2 + 10, withAlpha(0x666666, (int)(pulse * 180)));

        // Show selected config summary during countdown so player knows what they picked
        graphics.centeredText(this.font, Component.literal("§8" + buildSummary()), cx, this.height / 2 + 26, withAlpha(0x333344, 180));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (countingDown) return true; // block all clicks during countdown

        int cx     = this.width / 2;
        int panelW = Math.min(460, this.width - 40);
        int panelX = cx - panelW / 2;
        int rowH   = getRowH();
        int startY = getStartY();
        int gap    = 8;

        // Check each row for option button clicks
        for (int row = 0; row < 3; row++) {
            int y = startY + row * (rowH + gap);
            String[] options  = row == 0 ? DURATION_LABELS : row == 1 ? SPEED_LABELS : POOL_LABELS;
            int btnAreaW  = panelW - 130;
            int btnW      = (btnAreaW - (options.length - 1) * 4) / options.length;
            int btnStartX = panelX + panelW - btnAreaW - 10;

            for (int i = 0; i < options.length; i++) {
                int btnX = btnStartX + i * (btnW + 4);
                int btnY = y + 6;
                int btnH = rowH - 12;

                if (event.x() >= btnX && event.x() < btnX + btnW
                        && event.y() >= btnY && event.y() < btnY + btnH) {
                    switch (row) {
                        case 0 -> durationIndex = i;
                        case 1 -> speedIndex    = i;
                        case 2 -> poolIndex     = i;
                    }
                    playSelectSound();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !countingDown) {
            this.onClose();
            Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.ROULETTE));
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a plain-English summary of the current selected settings */
    private String buildSummary() {
        String duration = durationIndex == 3 ? "endless session" : DURATION_LABELS[durationIndex] + " game";
        String speed    = SPEED_LABELS[speedIndex].toLowerCase() + " spins (" + (int)SPEED_SECONDS[speedIndex] + "s)";
        String pool     = POOL_LABELS[poolIndex].toLowerCase();
        return duration + "  •  " + speed + "  •  " + pool;
    }

    private void playSound(float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 0.6f, pitch, false);
        }
    }

    private void playSelectSound() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3f, 1.3f, false);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}