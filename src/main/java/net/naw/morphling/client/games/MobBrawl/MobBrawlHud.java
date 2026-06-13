package net.naw.morphling.client.games.MobBrawl;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mob Brawl in-fight HUD — renders on top of the game without blocking input.

 * Registered once via register() when the fight phase begins.
 * Only renders when MobBrawlClient.isActive() is true.

 * Layout:
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  PlayerName  [MobFace] ██████░░  ❤❤❤  ⚔VS⚔  ❤❤❤  ░░██████ [MobFace]  OpponentName │
 *   │                              05:00                      │
 *   └─────────────────────────────────────────────────────────┘

 * - Player names above health bars
 * - Mob face icons next to health bars
 * - Health bars expand from center outward
 * - Life hearts shown between bars and VS
 * - Timer in center below VS
 * - Countdown overlay (3..2..1..GO!) displayed center screen
 * - Red flash overlay when you take damage
 */
public class MobBrawlHud {

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("morphling", "mob_brawl_hud");
    private static boolean registered = false;

    // Animation state
    private static float phaseTimer     = 0f;
    private static float damageFeedback = 0f;
    private static float lastMyHealth   = -1f;

    // Cached mob entities for face rendering
    private static LivingEntity myMorphEntity  = null;
    private static LivingEntity oppMorphEntity = null;
    private static EntityType<?> lastMyMorph   = null;
    private static EntityType<?> lastOppMorph  = null;

    public static void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.addLast(HUD_ID, MobBrawlHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (!MobBrawlClient.isActive() && MobBrawlClient.getCountdownFlash() <= 0f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        float dt = deltaTracker.getGameTimeDeltaPartialTick(false) * 0.05f;
        phaseTimer += dt;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int cx      = screenW / 2;

        // ── Damage feedback — red flash when we take damage ───────────────────
        float myHealth = MobBrawlClient.getMyHealth();
        if (lastMyHealth > 0 && myHealth < lastMyHealth) damageFeedback = 1.0f;
        lastMyHealth = myHealth;
        if (damageFeedback > 0) {
            int flashAlpha = (int)(damageFeedback * damageFeedback * 80);
            graphics.fill(0, 0, screenW, screenH, (flashAlpha << 24) | 0xFF0000);
            damageFeedback = Math.max(0f, damageFeedback - dt * 2f);
        }

        // ── Life lost flash ───────────────────────────────────────────────────
        float lifeFlash = MobBrawlClient.getLifeFlashTimer();
        if (lifeFlash > 0) {
            int alpha = (int)(lifeFlash * lifeFlash * 60);
            graphics.fill(0, 0, screenW, screenH, (alpha << 24) | 0xFFFFFF);
        }

        // ── Top HUD panel ─────────────────────────────────────────────────────
        int panelH = 38;
        graphics.fill(0, 0, screenW, panelH, 0x99050510);
        graphics.fill(0, panelH, screenW, panelH + 1, 0x88222233);

        // ── Cache mob entities for face rendering ─────────────────────────────
        EntityType<?> myMorph  = MobBrawlClient.getMyMorph();
        EntityType<?> oppMorph = MobBrawlClient.getOpponentMorph();

        if (myMorph != null && myMorph != lastMyMorph && mc.level != null) {
            lastMyMorph = myMorph;
            var e = myMorph.create(mc.level, EntitySpawnReason.LOAD);
            myMorphEntity = e instanceof LivingEntity le ? le : null;
        }
        if (oppMorph != null && oppMorph != lastOppMorph && mc.level != null) {
            lastOppMorph = oppMorph;
            var e = oppMorph.create(mc.level, EntitySpawnReason.LOAD);
            oppMorphEntity = e instanceof LivingEntity le ? le : null;
        }

        // ── Health bars — expand from center outward ──────────────────────────
        float myMaxHealth  = MobBrawlClient.getMyMaxHealth();
        float oppMaxHealth = MobBrawlClient.getOpponentMaxHealth();
        float myRatio      = myMaxHealth  > 0 ? Math.clamp(myHealth / myMaxHealth, 0f, 1f)                        : 0f;
        float oppRatio     = oppMaxHealth > 0 ? Math.clamp(MobBrawlClient.getOpponentHealth() / oppMaxHealth, 0f, 1f) : 0f;

        int faceSize = 16; // mob face icon size
        int barW     = (screenW / 2) - 70;
        int barH     = 7;
        int barY     = 26;

        // My bar — left side, grows leftward from center
        int myBarX = cx - 50 - barW;
        graphics.fill(myBarX, barY, myBarX + barW, barY + barH, 0xFF111122);
        int myFill = (int)(barW * myRatio);
        if (myFill > 0) graphics.fill(myBarX + barW - myFill, barY, myBarX + barW, barY + barH, healthColor(myRatio));
        drawBarBorder(graphics, myBarX, barY, barW, barH);

        // Opponent bar — right side, grows rightward from center
        int oppBarX = cx + 50;
        graphics.fill(oppBarX, barY, oppBarX + barW, barY + barH, 0xFF111122);
        int oppFill = (int)(barW * oppRatio);
        if (oppFill > 0) graphics.fill(oppBarX, barY, oppBarX + oppFill, barY + barH, healthColor(oppRatio));
        drawBarBorder(graphics, oppBarX, barY, barW, barH);

        // ── Mob face icons next to health bars ────────────────────────────────
        // My face — right of my bar (between bar and center gap)
        if (myMorphEntity != null) {
            int faceX = myBarX + barW + 2;
            int faceY = barY + barH + 2;
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, faceX, faceY - faceSize * 2, faceX + faceSize, faceY,
                        faceSize / 2, 0.0625f, 0, 0, myMorphEntity);
            } catch (Exception ignored) {}
        }

        // Opponent face — left of opponent bar
        if (oppMorphEntity != null) {
            int faceX = oppBarX - faceSize - 2;
            int faceY = barY + barH + 2;
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, faceX, faceY - faceSize * 2, faceX + faceSize, faceY,
                        faceSize / 2, 0.0625f, 0, 0, oppMorphEntity);
            } catch (Exception ignored) {}
        }

        // ── Lives ─────────────────────────────────────────────────────────────
        int myLives  = MobBrawlClient.getMyLives();
        int oppLives = MobBrawlClient.getOpponentLives();
        int maxLives = MobBrawlClient.getLives();

        StringBuilder myHeartsStr  = new StringBuilder();
        for (int l = 0; l < maxLives; l++) myHeartsStr.append(l < myLives ? "❤" : "♡");
        graphics.text(mc.font, net.minecraft.network.chat.Component.literal(myHeartsStr.toString()),
                cx - 48 - mc.font.width(myHeartsStr.toString()), barY - 10, 0xFFFF4444, false);

        StringBuilder oppHeartsStr = new StringBuilder();
        for (int l = 0; l < maxLives; l++) oppHeartsStr.append(l < oppLives ? "❤" : "♡");
        graphics.text(mc.font, net.minecraft.network.chat.Component.literal(oppHeartsStr.toString()),
                cx + 50, barY - 10, 0xFFFF4444, false);

        // ── VS badge — center ─────────────────────────────────────────────────
        float vsPulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        int vsColor = withAlpha(0xFF5555, 180 + (int)(vsPulse * 75));
        graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("⚔VS⚔"), cx, barY - 3, vsColor);

        // ── Player names above bars ───────────────────────────────────────────
        String myName       = mc.player != null ? mc.player.getName().getString() : "YOU";
        String myMorphName  = myMorph  != null ? myMorph.getDescription().getString()  : "???";
        String oppMorphName = oppMorph != null ? oppMorph.getDescription().getString() : "???";

        String myDisplay  = truncate(myName + " (" + myMorphName + ")", barW - 10, mc.font);
        String oppDisplay = truncate(MobBrawlClient.getOpponentName() + " (" + oppMorphName + ")", barW - 10, mc.font);
        graphics.text(mc.font, net.minecraft.network.chat.Component.literal(myDisplay),  myBarX + barW - mc.font.width(myDisplay),  barY - 22, 0xFF5599FF, false);
        graphics.text(mc.font, net.minecraft.network.chat.Component.literal(oppDisplay), oppBarX, barY - 22, 0xFFFF5555, false);

        // ── Timer ─────────────────────────────────────────────────────────────
        float gameTimer = MobBrawlClient.getLocalTimer();
        int timeLimitSetting = MobBrawlClient.getTimeLimitSetting();

        if (timeLimitSetting > 0 && gameTimer >= 0) {
            int secsLeft = (int)Math.ceil(gameTimer);
            String timeStr = String.format("%02d:%02d", secsLeft / 60, secsLeft % 60);
            int timerColor;
            if (secsLeft <= 60) {
                float urgency = (float)(Math.sin(phaseTimer * (secsLeft <= 10 ? 8 : 3)) * 0.5 + 0.5);
                timerColor = withAlpha(0xFF3333, 180 + (int)(urgency * 75));
            } else {
                timerColor = 0xFF888899;
            }
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal(timeStr), cx, barY + barH - 25, timerColor);
        } else {
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§8∞"), cx, barY + barH - 25, 0xFF333355);
        }

        // ── [G] pause hint ────────────────────────────────────────────────────
        //String pauseKey = net.naw.morphling.client.MorphlingClient.openMenuKey != null
                //? net.naw.morphling.client.MorphlingClient.openMenuKey.getTranslatedKeyMessage().getString()
                //: "G";
        //graphics.text(mc.font, net.minecraft.network.chat.Component.literal("§8[" + pauseKey + "] pause"),
                //6, 6, 0xFF1A1A33, false);

        // ── Countdown overlay ─────────────────────────────────────────────────
        renderCountdown(graphics, screenW, screenH, cx);
    }

    private static void renderCountdown(GuiGraphicsExtractor graphics, int screenW, int screenH, int cx) {
        float flash = MobBrawlClient.getCountdownFlash();
        if (flash <= 0f) return;

        int count = MobBrawlClient.getCountdownValue();
        Minecraft mc = Minecraft.getInstance();

        graphics.fill(0, 0, screenW, screenH, withAlpha(0x000000, (int)(flash * 120)));

        if (count == 0) {
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("⚔  GO!  ⚔"),
                    cx, screenH / 2 - 10, withAlpha(0x55FF55, (int)(flash * 255)));
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§8Fight!"),
                    cx, screenH / 2 + 6, withAlpha(0x888888, (int)(flash * 200)));
        } else {
            float numPulse = (float)(Math.sin(flash * Math.PI) * 0.5 + 0.5);
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal(String.valueOf(count)),
                    cx, screenH / 2 - 10, withAlpha(0xFF5555, 150 + (int)(numPulse * 105)));
            graphics.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§8Get ready..."),
                    cx, screenH / 2 + 6, withAlpha(0x666666, (int)(flash * 180)));
        }
    }

    private static String truncate(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 1 && font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private static int healthColor(float ratio) {
        if (ratio > 0.5f) {
            float s = (ratio - 0.5f) * 2f;
            int r   = (int)((1f - s) * 0xFF);
            return 0xFF000000 | (r << 16) | (0xFF << 8);
        } else {
            float s = ratio * 2f;
            int g   = (int)(s * 0xFF);
            return 0xFF000000 | (0xFF << 16) | (g << 8);
        }
    }

    private static void drawBarBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x,       y,       x + w, y + 1,   0xFF334455);
        graphics.fill(x,       y + h-1, x + w, y + h,   0xFF334455);
        graphics.fill(x,       y,       x + 1, y + h,   0xFF334455);
        graphics.fill(x + w-1, y,       x + w, y + h,   0xFF334455);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }
}