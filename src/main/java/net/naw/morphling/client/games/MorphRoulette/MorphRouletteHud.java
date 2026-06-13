package net.naw.morphling.client.games.MorphRoulette;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * Morph Roulette HUD overlay — renders on top of the game without blocking input.

 * Registered once via register() which is called from MorphRouletteGame.start().
 * Only renders when MorphRouletteGame.isRunning() is true.
 * Game logic is ticked in MorphlingClient.END_CLIENT_TICK at 20tps for accuracy.

 * Layout — compact top-right corner panel:
 *   - Small dark card in top-right corner (~30% of screen width)
 *   - Current morph name with blue pulse
 *   - Countdown bar (compact, fits inside card)
 *   - Score + spin count below bar
 *   - [G] pause hint at bottom of card
 *   - NEW MORPH notification appears inside the card on spin
 *   - Full screen color flash on spin (subtle)
 */
public class MorphRouletteHud {

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("morphling", "roulette_hud");
    private static boolean registered = false;

    // Animation state — static since HUD is a singleton callback
    private static float phaseTimer = 0f;
    private static float hue        = 0f;

    private static float lastTickSound = 0f;

    /** Registers the HUD render callback — safe to call multiple times, only registers once */
    public static void register() {
        if (registered) return;
        registered = true;

        HudElementRegistry.addLast(
                HUD_ID,
                MorphRouletteHud::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        MorphRouletteGame game = MorphRouletteGame.getInstance();
        if (!game.isRunning()) return;

        Minecraft mc = Minecraft.getInstance();

        // Don't render HUD when a screen is open (pause overlay or end screen)
        if (mc.screen != null) return;

        float animDt = deltaTracker.getGameTimeDeltaPartialTick(false) * 0.05f;
        phaseTimer += animDt;
        hue = (hue + animDt * 0.3f) % 1.0f;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        // ── Tick sound when under 3 seconds — speeds up as countdown approaches 0 ──
        if (game.getCountdown() < 3f && mc.level != null && mc.player != null) {
            int secsLeft = (int)Math.ceil(game.getCountdown());
            int ticksPerSec = Math.max(1, 4 - secsLeft); // 3s=1, 2s=2, 1s=3
            float tickInterval = (1f / ticksPerSec) * 2f;
            if (phaseTimer - lastTickSound >= tickInterval) {
                lastTickSound = phaseTimer;
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.1f, 1.5f, false);
            }
        }

        // ── Subtle full screen flash on spin ──────────────────────────────────
        float flash = game.getSpinFlash();
        if (flash > 0) {
            float prog       = flash / MorphRouletteGame.SPIN_FLASH_DURATION;
            int   flashAlpha = (int)(prog * prog * 30); // very subtle
            graphics.fill(0, 0, screenW, screenH, (flashAlpha << 24) | 0x5599FF);
        }

        // ── Compact top-right panel ───────────────────────────────────────────
        // Panel is ~30% of screen width, tucked in top-right corner
        int panelW = Math.min(130, screenW / 4);
        int panelH = 80;
        int panelX = screenW - panelW - 4;
        int panelY = 4;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC050510);

        // Blue accent border
        float borderPulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        int   borderColor = withAlpha(0x5599FF, (int)(80 + borderPulse * 40));
        graphics.fill(panelX,             panelY,             panelX + panelW, panelY + 1,       borderColor);
        graphics.fill(panelX,             panelY + panelH - 1, panelX + panelW, panelY + panelH, borderColor);
        graphics.fill(panelX,             panelY,             panelX + 1,      panelY + panelH,  borderColor);
        graphics.fill(panelX + panelW - 1, panelY,            panelX + panelW, panelY + panelH,  borderColor);

        int cx = panelX + panelW / 2;

        // ── NEW MORPH notification inside panel ───────────────────────────────
        // Shows briefly after each spin, then fades to normal morph name display
        EntityType<?> morph    = game.getCurrentMorph();
        String        morphName = morph != null ? morph.getDescription().getString() : "???";

        if (flash > 0) {
            // During flash — show NEW MORPH notification
            float prog  = flash / MorphRouletteGame.SPIN_FLASH_DURATION;
            int   alpha = (int)(prog * 255);
            int   rgb2  = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("✦ NEW MORPH! ✦"),
                    cx, panelY + 8, withAlpha(rgb2 & 0x00FFFFFF, alpha));
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§f" + morphName.toUpperCase()),
                    cx, panelY + 20, withAlpha(0xFFFFFF, alpha));
        } else {
            // Normal display — current morph name with blue pulse
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§8Morphed into:"),
                    cx, panelY + 6, 0xFF222244);
            float morphPulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
            int   morphColor = withAlpha(0x5599FF, 180 + (int)(morphPulse * 75));
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal(morphName.toUpperCase()),
                    cx, panelY + 18, morphColor);
        }

        // ── Countdown bar ─────────────────────────────────────────────────────
        float spinInterval = game.getConfigSpinInterval();
        float ratio  = spinInterval > 0 ? Math.clamp(game.getCountdown() / spinInterval, 0f, 1f) : 0f;
        int barW = panelW - 16;
        int barX = panelX + 8;
        int barY = panelY + 32;
        int barH = 5;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF111122);
        graphics.fill(barX, barY, barX + (int)(barW * ratio), barY + barH, lerpBarColor(ratio));

        // Pulse glow when under 5 seconds
        if (game.getCountdown() < 5f) {
            float pulse     = (float)(Math.sin(phaseTimer * 10) * 0.5 + 0.5);
            int   glowAlpha = (int)(pulse * 50);
            graphics.fill(barX, barY - 1, barX + (int)(barW * ratio), barY + barH + 1,
                    (glowAlpha << 24) | 0xFF3333);
        }

        // Countdown seconds text
        int secsLeft = (int)Math.ceil(game.getCountdown());
        graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§8" + secsLeft + "s"),
                cx, barY + barH + 3, 0xFF222244);

        // ── Game timer — shows elapsed or remaining time ──────────────────────
// Endless: counts up in white. Timed: counts down, pulses red when under 60s.
        String timeStr;
        int    timeColor;
        if (game.getConfigDurationSeconds() < 0) {
            // Endless — count up, white
            timeStr  = "⏱ " + formatMMSS((int)game.getElapsed());
            timeColor = 0xFFCCCCCC;
        } else {
            // Timed — count down
            int remaining = Math.max(0, game.getConfigDurationSeconds() - (int)game.getElapsed());
            timeStr = "⏱ " + formatMMSS(remaining);
            // Pulse orange→red when under 60 seconds
            if (remaining <= 60) {
                float urgency = (float)(Math.sin(phaseTimer * (remaining <= 10 ? 8 : 3)) * 0.5 + 0.5);
                int r = 0xFF;
                int g = (int)(urgency * (remaining <= 10 ? 0 : 80));
                timeColor = 0xFF000000 | (r << 16) | (g << 8);
            } else {
                timeColor = 0xFFCCCCCC;
            }
        }
        graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal(timeStr), cx - 25, panelY + 56, timeColor);

        // Score — centered in panel
        graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§e⭐ " + game.getScore()),
                cx + 10, panelY + 56, 0xFFFFAA00);

// Spin count — right side of panel
        String spinStr = "#" + game.getSpinCount();
        graphics.text(mc.font, net.minecraft.network.chat.Component.literal("§8" + spinStr),
                panelX + panelW - mc.font.width(spinStr) - 6, panelY + 56, 0xFF333355, false);

        // ── [G] pause hint ────────────────────────────────────────────────────
// Dynamic keybind label — shows actual assigned key
        String pauseKey = net.naw.morphling.client.MorphlingClient.openMenuKey != null
                ? net.naw.morphling.client.MorphlingClient.openMenuKey.getTranslatedKeyMessage().getString()
                : "G";
        graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§8[" + pauseKey + "] pause"),
                cx, panelY + 68, 0xFF1A1A33);
    }

    /** Lerps countdown bar color: green (full) → yellow → red (empty) */
    private static int lerpBarColor(float t) {
        if (t > 0.5f) {
            float s = (t - 0.5f) * 2f;
            int   r = (int)((1f - s) * 0xFF);
            return 0xFF000000 | (r << 16) | (0xFF << 8);
        } else {
            float s = t * 2f;
            int   g = (int)(s * 0xFF);
            return 0xFF000000 | (0xFF << 16) | (g << 8);
        }
    }

    /** Formats seconds into MM:SS display */
    private static String formatMMSS(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }
}