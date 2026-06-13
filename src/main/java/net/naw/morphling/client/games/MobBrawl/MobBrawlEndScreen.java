package net.naw.morphling.client.games.MobBrawl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.games.ui.MorphGameModeSelect;
import net.naw.morphling.client.games.ui.RoomBrowserScreen;
import org.jspecify.annotations.NonNull;

/**
 * Mob Brawl end screen — shown to both players when the fight ends.

 * Shows win or loss state with dramatic visual styling:

 * WIN:
 *   - Gold "VICTORY!" title with radiant pulse
 *   - Your morph entity shown large in the center doing a victory pose
 *   - Stats: damage dealt, lives remaining, opponent's morph
 *   - Firework-style particle effect in background
 *   - Triumphant fanfare sound

 * LOSE:
 *   - Dark red "DEFEATED" title
 *   - Your morph shown small, opponent's morph shown large
 *   - Stats: damage dealt, lives lost
 *   - Somber sound

 * Buttons (appear after 2.5s):
 *   ▶ Play Again  — back to Mob Brawl room browser
 *   ← Main Menu  — back to mode select

 * Timeline:
 *   0.0 - 0.5s : Title fades in
 *   0.5 - 1.5s : Stats roll in one by one
 *   1.5 - 2.0s : Morph entity animates in
 *   2.5s+      : Buttons appear
 */
public class MobBrawlEndScreen extends Screen {

    // ── State ─────────────────────────────────────────────────────────────────
    private final boolean     didWin;
    private final EntityType<?> myMorph;
    private final EntityType<?> opponentMorph;
    private final float       myDamage;
    private final float       oppDamage;
    private final int         myLivesLeft;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final int    oppLivesLeft;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final String roomId;
    private final String myName;
    private final String opponentName;

    // ── Animation ─────────────────────────────────────────────────────────────
    private float endTimer   = 0f;
    private float phaseTimer = 0f;
    private float hue        = 0f;

    // ── Entity previews ───────────────────────────────────────────────────────
    private LivingEntity myEntity  = null;
    private LivingEntity oppEntity = null;

    // ── Particle state (win only) ─────────────────────────────────────────────
    private static final int   PARTICLE_COUNT = 30;
    private final float[]      pX             = new float[PARTICLE_COUNT];
    private final float[]      pY             = new float[PARTICLE_COUNT];
    private final float[]      pVX            = new float[PARTICLE_COUNT];
    private final float[]      pVY            = new float[PARTICLE_COUNT];
    private final float[]      pLife          = new float[PARTICLE_COUNT];
    private final int[]        pColor         = new int[PARTICLE_COUNT];
    private boolean            particlesInit  = false;

    // ── Button hover ──────────────────────────────────────────────────────────
    private float playAgainHover = 0f;
    private float mainMenuHover  = 0f;

    // ── Sound flags ───────────────────────────────────────────────────────────
    private boolean titleSoundPlayed = false;

    public MobBrawlEndScreen(boolean didWin, EntityType<?> myMorph, EntityType<?> opponentMorph,
                             float myDamage, float oppDamage, int myLivesLeft, int oppLivesLeft,
                             String roomId, String myName, String opponentName) {
        super(Component.literal(didWin ? "Victory!" : "Defeated"));
        this.didWin        = didWin;
        this.myMorph       = myMorph;
        this.opponentMorph = opponentMorph;
        this.myDamage      = myDamage;
        this.oppDamage     = oppDamage;
        this.myLivesLeft   = myLivesLeft;
        this.oppLivesLeft  = oppLivesLeft;
        this.roomId        = roomId;
        this.myName        = myName;
        this.opponentName  = opponentName;
    }

    @Override
    protected void init() {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            if (myMorph != null) {
                var e = myMorph.create(level, EntitySpawnReason.LOAD);
                if (e instanceof LivingEntity le) myEntity = le;
            }
            if (opponentMorph != null) {
                var e = opponentMorph.create(level, EntitySpawnReason.LOAD);
                if (e instanceof LivingEntity le) oppEntity = le;
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        MobBrawlClient.tickOpponentMouseSmoothing(dt);
        endTimer   += dt;
        phaseTimer += dt;
        hue = (hue + dt * (didWin ? 0.4f : 0.1f)) % 1.0f;

        int cx = this.width / 2;
        int cy = this.height / 2;

        // ── Background ────────────────────────────────────────────────────────
        if (didWin) {
            // Dark with warm golden undertone
            graphics.fill(0, 0, this.width, this.height, 0xF0080600);
        } else {
            // Dark with cold red undertone
            graphics.fill(0, 0, this.width, this.height, 0xF0080000);
        }

        // ── Win particles ─────────────────────────────────────────────────────
        if (didWin) renderParticles(graphics, dt);

        // ── Title ─────────────────────────────────────────────────────────────
        if (!titleSoundPlayed && endTimer > 0.1f) {
            titleSoundPlayed = true;
            if (didWin) {
                playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.8f);
            }
        }
        if (!didWin) {
            if (endTimer > 0.1f && endTimer < 0.15f) playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.8f);
            if (endTimer > 0.5f && endTimer < 0.55f) playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
            if (endTimer > 0.9f && endTimer < 0.95f) playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.4f);
        }

        float titleAlpha = Math.min(1f, endTimer / 0.5f);
        if (didWin) {
            // Gold pulsing VICTORY
            float pulse = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
            int   rgb   = java.awt.Color.HSBtoRGB(0.12f + pulse * 0.05f, 1f, 1f);
            int   color = withAlpha(rgb & 0x00FFFFFF, (int)(titleAlpha * 255));
            graphics.centeredText(this.font, Component.literal("✦  VICTORY!  ✦"), cx, cy - 95, color);
        } else {
            // Dark red DEFEATED
            float pulse = (float)(Math.sin(phaseTimer * 1.5f) * 0.3 + 0.7);
            int   color = withAlpha(0xFF3333, (int)(titleAlpha * pulse * 220));
            graphics.centeredText(this.font, Component.literal("✖  DEFEATED  ✖"), cx, cy - 95, color);
        }

        // ── Morph entity preview ──────────────────────────────────────────────
        if (endTimer > 1.5f) {
            float entityAlpha = Math.min(1f, (endTimer - 1.5f) / 0.5f);

            if (didWin) {
                // WIN — your morph large and centered, glowing
                if (myEntity != null) {
                    int size = (int)(40 * entityAlpha);
                    try {
                        InventoryScreen.extractEntityInInventoryFollowsMouse(
                                graphics, cx - size, cy - 60, cx + size, cy + 10,
                                size, 0.0625f, mouseX, mouseY, myEntity);
                    } catch (Exception ignored) {}
                    graphics.centeredText(this.font, Component.literal(myName),
                            cx, cy - 75, withAlpha(0xFFFFFF, (int)(180 * entityAlpha)));
                }

                // Small opponent morph bottom left
                if (oppEntity != null) {
                    int size = (int)(18 * entityAlpha);
                    try {
                        InventoryScreen.extractEntityInInventoryFollowsMouse(
                                graphics, cx - 80 - size, cy - 10, cx - 80 + size, cy + 26,
                                size, 0.0625f, (int) MobBrawlClient.getOpponentMouseX(), (int) MobBrawlClient.getOpponentMouseY(), oppEntity);
                    } catch (Exception ignored) {}
                    graphics.centeredText(this.font, Component.literal(opponentName),
                            cx - 80, cy - 14, withAlpha(0xFFFFFF, (int)(120 * entityAlpha)));
                }

            } else {
                // LOSE — opponent large center, your morph small bottom left
                if (oppEntity != null) {
                    int size = (int)(36 * entityAlpha);
                    try {
                        InventoryScreen.extractEntityInInventoryFollowsMouse(
                                graphics, cx - size, cy - 60, cx + size, cy + 10,
                                size, 0.0625f, (int) MobBrawlClient.getOpponentMouseX(), (int) MobBrawlClient.getOpponentMouseY(), oppEntity);
                    } catch (Exception ignored) {}
                    graphics.centeredText(this.font, Component.literal(opponentName),
                            cx, cy - 75, withAlpha(0xFFFFFF, (int)(180 * entityAlpha)));
                }
                if (myEntity != null) {
                    int size = (int)(18 * entityAlpha);
                    try {
                        InventoryScreen.extractEntityInInventoryFollowsMouse(
                                graphics, cx - 80 - size, cy - 10, cx - 80 + size, cy + 26,
                                size, 0.0625f, mouseX, mouseY, myEntity);
                    } catch (Exception ignored) {}
                    graphics.centeredText(this.font, Component.literal(myName),
                            cx - 80, cy - 14, withAlpha(0xFFFFFF, (int)(120 * entityAlpha)));
                }
            }
        }

        // ── Stats ─────────────────────────────────────────────────────────────
        if (endTimer > 0.5f) {
            float statsAlpha = Math.min(1f, (endTimer - 0.5f));
            int   sAlpha     = (int)(statsAlpha * 255);
            int   statY      = cy + 20;
            int   statGap    = 12;

            // Damage dealt
            graphics.centeredText(this.font,
                    Component.literal("§7Damage dealt: §f" + String.format("%.1f", myDamage)),
                    cx, statY, withAlpha(0x888888, sAlpha));

            // Opponent damage
            graphics.centeredText(this.font,
                    Component.literal("§7Opponent dealt: §c" + String.format("%.1f", oppDamage)),
                    cx, statY + statGap, withAlpha(0x888888, sAlpha));

            // Lives remaining
            String livesStr = didWin
                    ? "§7Lives remaining: §a" + myLivesLeft
                    : "§7Lives remaining: §c" + myLivesLeft;
            graphics.centeredText(this.font, Component.literal(livesStr),
                    cx, statY + statGap * 2, withAlpha(0x888888, sAlpha));

            // Morph used
            String morphName = myMorph != null ? myMorph.getDescription().getString() : "???";
            graphics.centeredText(this.font,
                    Component.literal("§7Your morph: §f" + morphName),
                    cx, statY + statGap * 3, withAlpha(0x888888, sAlpha));
        }

        // ── Buttons ───────────────────────────────────────────────────────────
        if (endTimer > 2.5f) {
            float btnAlpha = Math.min(1f, (endTimer - 2.5f) / 0.4f);
            int   btnW     = 140, btnH = 20;
            int   btnX     = cx - btnW / 2;
            int   btnY1    = this.height - 50;
            int   btnY2    = this.height - 26;

            // Play Again
            boolean paHov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY1 && mouseY < btnY1 + btnH;
            playAgainHover += ((paHov ? 1f : 0f) - playAgainHover) * 0.2f;
            renderBtn(graphics, btnX, btnY1, btnW, btnH, "▶  Play Again",
                    0xFF55FF55, playAgainHover, btnAlpha);

            // Main Menu
            boolean mmHov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY2 && mouseY < btnY2 + btnH;
            mainMenuHover += ((mmHov ? 1f : 0f) - mainMenuHover) * 0.2f;
            renderBtn(graphics, btnX, btnY2, btnW, btnH, "← Main Menu",
                    0xFFFF5555, mainMenuHover, btnAlpha);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── Win particles ─────────────────────────────────────────────────────────

    private void renderParticles(GuiGraphicsExtractor graphics, float dt) {
        if (!particlesInit) {
            particlesInit = true;
            java.util.Random rng = new java.util.Random();
            int[] colors = {0xFFFF55, 0xFF5555, 0x55FF55, 0x55FFFF, 0xFF55FF, 0xFFFFFF};
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                pX[i]     = this.width / 2f + (rng.nextFloat() - 0.5f) * this.width * 0.6f;
                pY[i]     = this.height / 2f + (rng.nextFloat() - 0.5f) * this.height * 0.4f;
                pVX[i]    = (rng.nextFloat() - 0.5f) * 60f;
                pVY[i]    = -rng.nextFloat() * 80f - 20f;
                pLife[i]  = rng.nextFloat() * 3f + 1f;
                pColor[i] = colors[rng.nextInt(colors.length)];
            }
        }

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            pX[i]    += pVX[i] * dt;
            pY[i]    += pVY[i] * dt;
            pVY[i]   += 40f * dt; // gravity
            pLife[i] -= dt;

            if (pLife[i] <= 0) {
                // Respawn
                java.util.Random rng = new java.util.Random();
                pX[i]    = this.width / 2f + (rng.nextFloat() - 0.5f) * this.width * 0.8f;
                pY[i]    = -10f;
                pVX[i]   = (rng.nextFloat() - 0.5f) * 40f;
                pVY[i]   = rng.nextFloat() * 20f;
                pLife[i] = rng.nextFloat() * 3f + 1f;
            }

            if (pLife[i] > 0) {
                int alpha = (int)(Math.min(1f, pLife[i]) * 200);
                int size  = 3;
                graphics.fill((int)pX[i], (int)pY[i], (int)pX[i] + size, (int)pY[i] + size,
                        withAlpha(pColor[i], alpha));
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (endTimer <= 2.5f) return true;

        int cx   = this.width / 2;
        int btnW = 140, btnH = 20;
        int btnX = cx - btnW / 2;
        int btnY1 = this.height - 50;
        int btnY2 = this.height - 26;

        if (event.x() >= btnX && event.x() < btnX + btnW && event.y() >= btnY1 && event.y() < btnY1 + btnH) {
            // Play Again — back to Mob Brawl room browser
            playClickSound();
            MobBrawlClient.clearSession();
            Minecraft.getInstance().setScreen(
                    new RoomBrowserScreen(MorphGameModeSelect.GameMode.MOB_BRAWL));
            return true;
        }

        if (event.x() >= btnX && event.x() < btnX + btnW && event.y() >= btnY2 && event.y() < btnY2 + btnH) {
            // Main Menu — leave room and back to mode select
            playClickSound();

            if (roomId != null && Minecraft.getInstance().player != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new net.naw.morphling.client.games.packet.RoomsNetworking.RoomLeavePayload(
                                Minecraft.getInstance().player.getUUID(),
                                roomId));
            }
            MobBrawlClient.clearSession();
            RoomBrowserScreen.lastJoinedRoomId   = null;
            RoomBrowserScreen.lastRoomHost       = null;
            RoomBrowserScreen.lastRoomPlayers    = new String[0];
            RoomBrowserScreen.lastRoomName       = null;
            Minecraft.getInstance().setScreen(
                    new MorphGameModeSelect(MorphGameModeSelect.GameMode.MOB_BRAWL));
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true; // block escape
        return super.keyPressed(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void renderBtn(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                           String label, int color, float hover, float alpha) {
        int bg     = withAlpha(color & 0x00FFFFFF, (int)((hover * 40 + 10) * alpha));
        int border = withAlpha(color & 0x00FFFFFF, (int)((hover * 120 + 60) * alpha));
        graphics.fill(x,       y,       x + w, y + h, bg);
        graphics.fill(x,       y,       x + w, y + 1,   border);
        graphics.fill(x,       y + h-1, x + w, y + h,   border);
        graphics.fill(x,       y,       x + 1, y + h,   border);
        graphics.fill(x + w-1, y,       x + w, y + h,   border);
        graphics.centeredText(this.font, Component.literal(label), x + w / 2, y + 6,
                withAlpha(color, (int)(alpha * 255)));
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    private void playClickSound() {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}