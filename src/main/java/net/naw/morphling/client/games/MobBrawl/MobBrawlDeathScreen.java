package net.naw.morphling.client.games.MobBrawl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Mob Brawl death screen — shown for 3 seconds after a player dies mid-fight.

 * Visual style:
 *   - Semi-transparent dark red overlay (you can still see the world behind it)
 *   - Ghostly morph entity in center, faded and slightly desaturated
 *   - Skull ☠ icon with a dramatic pulse
 *   - Circular arc timer depleting over 3 seconds
 *   - "Respawning..." text with a blinking dot
 *   - Lives remaining shown below
 *   - Screen fades out as timer hits 0

 * This screen closes itself automatically when the server teleports the player
 * (MobBrawlClient clears it). Input is fully blocked while shown.
 */
public class MobBrawlDeathScreen extends Screen {

    // ── Timer ─────────────────────────────────────────────────────────────────
    private static final float DEATH_DURATION = 3.0f; // seconds
    private float timer = 0f;

    // ── Entity preview ────────────────────────────────────────────────────────
    private final EntityType<?> myMorph;
    private LivingEntity morphEntity = null;

    // ── Lives ─────────────────────────────────────────────────────────────────
    private final int livesLeft;

    // ── Animation ─────────────────────────────────────────────────────────────
    private float phaseTimer = 0f;

    public MobBrawlDeathScreen(EntityType<?> myMorph, int livesLeft) {
        super(Component.literal("You died"));
        this.myMorph   = myMorph;
        this.livesLeft = livesLeft;
    }

    @Override
    protected void init() {
        var level = Minecraft.getInstance().level;
        if (level != null && myMorph != null) {
            var e = myMorph.create(level, EntitySpawnReason.LOAD);
            if (e instanceof LivingEntity le) morphEntity = le;
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        timer      += dt;
        phaseTimer += dt;

        int cx = this.width  / 2;
        int cy = this.height / 2;

        float progress  = Math.min(1f, timer / DEATH_DURATION); // 0 → 1 over 3s
        float fadeIn    = Math.min(1f, timer / 0.3f);           // fast fade in
        float fadeOut   = timer > DEATH_DURATION - 0.4f         // fade out near end
                ? 1f - (timer - (DEATH_DURATION - 0.4f)) / 0.4f
                : 1f;
        float alpha     = fadeIn * fadeOut;

        // ── Background overlay ────────────────────────────────────────────────
        // Dark red semi-transparent — you can still see the world
        graphics.fill(0, 0, this.width, this.height,
                withAlpha(0x1A0000, (int)(alpha * 180)));

        // ── Vignette — darker edges ───────────────────────────────────────────
        int vigW = this.width / 3;
        int vigH = this.height / 3;
        graphics.fill(0,              0,               vigW,          this.height, withAlpha(0x000000, (int)(alpha * 80)));
        graphics.fill(this.width-vigW,0,               this.width,    this.height, withAlpha(0x000000, (int)(alpha * 80)));
        graphics.fill(0,              0,               this.width,    vigH,        withAlpha(0x000000, (int)(alpha * 80)));
        graphics.fill(0,              this.height-vigH,this.width,    this.height, withAlpha(0x000000, (int)(alpha * 80)));

        // ── Ghost morph entity ────────────────────────────────────────────────
        if (morphEntity != null) {
            // Ghostly — low opacity, centered
            int size = 36;
            @SuppressWarnings("unused") float entityAlpha = alpha * 0.45f; // semi-transparent ghost feel
            // We can't set per-draw alpha on InventoryScreen directly,
            // so we render at a fixed size and let the overlay dim it naturally
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, cx - size, cy - 55, cx + size, cy + 5,
                        size, 0.0625f, cx, cy - 20, morphEntity);
            } catch (Exception ignored) {}
        }

        // ── Skull icon + pulse ────────────────────────────────────────────────
        float skullPulse = (float)(Math.sin(phaseTimer * 4) * 0.5 + 0.5);
        int   skullAlpha = (int)(alpha * (180 + skullPulse * 75));
        int   skullColor = withAlpha(0xFF3333, skullAlpha);
        graphics.centeredText(this.font, Component.literal("☠"), cx, cy - 70, skullColor);

        // ── Circular arc timer ────────────────────────────────────────────────
        // Drawn as a series of small filled rectangles around a circle
        int   arcR     = 22;
        int   arcY     = cy + 28;
        float arcLeft  = 1f - progress; // 1 → 0 as time runs out
        int   arcSegs  = 48;
        for (int i = 0; i < arcSegs; i++) {
            float segFrac = i / (float) arcSegs;
            if (segFrac > arcLeft) break; // only draw remaining portion
            double angle = Math.toRadians(segFrac * 360 - 90); // start from top
            int sx = (int)(cx + Math.cos(angle) * arcR);
            int sy = (int)(arcY + Math.sin(angle) * arcR);
            // Color shifts from red → dark red as time runs out
            int r = (int)(255 * arcLeft + 80 * (1 - arcLeft));
            int g = (int)(80  * arcLeft);
            int arcColor = withAlpha((r << 16) | (g << 8), (int)(alpha * 220));
            graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, arcColor);
        }

        // Arc background (dim track)
        for (int i = 0; i < arcSegs; i++) {
            double angle = Math.toRadians((i / (float) arcSegs) * 360 - 90);
            int sx = (int)(cx + Math.cos(angle) * arcR);
            int sy = (int)(arcY + Math.sin(angle) * arcR);
            graphics.fill(sx, sy, sx + 1, sy + 1, withAlpha(0x333333, (int)(alpha * 80)));
        }

        // ── Countdown number ──────────────────────────────────────────────────
        int secondsLeft = Math.max(1, (int)Math.ceil(DEATH_DURATION - timer));
        float numPulse  = (float)(Math.sin(phaseTimer * 6) * 0.15 + 0.85);
        graphics.centeredText(this.font,
                Component.literal("§c" + secondsLeft),
                cx, arcY - 4,
                withAlpha(0xFF5555, (int)(alpha * numPulse * 255)));

        // ── "Respawning..." text ──────────────────────────────────────────────
        int   dotCount  = ((int)(phaseTimer * 3)) % 4; // 0..3 cycling dots
        String dots     = ".".repeat(dotCount);
        String padded   = dots + "   ".substring(Math.min(3, dots.length())); // keep width stable
        graphics.centeredText(this.font,
                Component.literal("§7Respawning" + padded),
                cx, arcY + 30,
                withAlpha(0x888888, (int)(alpha * 200)));

        // ── Lives remaining ───────────────────────────────────────────────────
        if (livesLeft > 0) {
            StringBuilder hearts = new StringBuilder();
            hearts.repeat("❤ ", livesLeft);
            graphics.centeredText(this.font,
                    Component.literal("§c" + hearts.toString().trim()),
                    cx, arcY + 44,
                    withAlpha(0xFF5555, (int)(alpha * 180)));
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // ── Auto-close when timer expires ─────────────────────────────────────
        if (timer >= DEATH_DURATION) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    // ── Block all input ───────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        return true; // eat all clicks
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        return true; // eat all keys
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }
}