package net.naw.morphling.client.games.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.naw.morphling.client.games.trivia.MorphTriviaScreen;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

/**
 * Mode select screen — shown before launching any Solo/Multi game mode.
 * Pass a GameMode enum value to specify which game to launch.

 * Currently implemented:
 *   TRIVIA → MorphTriviaScreen (solo only for now)

 * Multiplayer shows "Coming Soon" for all modes until networking is built.
 */
public class MorphGameModeSelect extends Screen {

    public enum GameMode {
        TRIVIA       ("🧠 Morph Trivia",   "Guess the mob from its ability.",    EntityType.VILLAGER),
        ROULETTE     ("🎲 Morph Roulette", "Survive with a random morph.",       EntityType.CREEPER),
        RELAY_RACE   ("🏁 Relay Race",     "New morph at every checkpoint.",      EntityType.PARROT),
        MOB_BRAWL    ("⚔ Mob Brawl",      "1v1 arena. Abilities only.",          EntityType.WOLF),
        ;

        final String title;
        final String description;
        final EntityType<?> previewMob;

        GameMode(String title, String description, EntityType<?> previewMob) {
            this.title       = title;
            this.description = description;
            this.previewMob  = previewMob;
        }
    }

    private final GameMode gameMode;
    private LivingEntity previewEntity;
    private float hue        = 0f;
    private float phaseTimer = 0f;
    private float introAlpha = 0f;
    private long handCursor = 0L;

    // Hover state for the two cards
    private float soloHover  = 0f;
    private float multiHover = 0f;

    public MorphGameModeSelect(GameMode gameMode) {
        super(Component.literal(gameMode.title));
        this.gameMode = gameMode;
    }

    @Override
    protected void init() {
        // Back button
        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> { this.onClose(); Minecraft.getInstance().setScreen(new MorphGamesScreen()); }
        ).bounds(10, 10, 60, 20).build());


        // Spawn preview entity
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var e = gameMode.previewMob.create(level, EntitySpawnReason.LOAD);
            if (e instanceof LivingEntity le) previewEntity = le;
        }

        handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = 0.016f;
        hue        = (hue + 0.004f) % 1.0f;
        phaseTimer += dt;
        introAlpha  = Math.min(1f, introAlpha + dt * 2f);

        // Background
        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        // Subtle animated spotlight lines
        for (int i = 0; i < 3; i++) {
            float prog = ((phaseTimer * 0.2f + i * 0.33f) % 1.0f);
            int alpha = (int)(Math.sin(prog * Math.PI) * 2);
            int lx = (int)(prog * this.width * 1.5f) - this.width / 4;
            graphics.fill(lx, 0, lx + 80, this.height, (alpha << 24) | 0xFFFFFF);
        }

        int cx = this.width / 2;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        int rainbowColor = 0xFF000000 | (rgb & 0x00FFFFFF);

        // Title
        int titleAlpha = (int)(introAlpha * 255);
        float titlePulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        int displayColor = gameMode == GameMode.ROULETTE
                ? withAlpha(0x5599FF, (int)(180 + titlePulse * 75))
                : gameMode == GameMode.MOB_BRAWL
                  ? withAlpha(0x5599FF, (int)(180 + titlePulse * 75))
                  : gameMode == GameMode.TRIVIA
                    ? withAlpha(0xCC55FF, (int)(180 + titlePulse * 75))
                    : withAlpha(rainbowColor & 0x00FFFFFF, titleAlpha);

        graphics.centeredText(this.font, Component.literal(gameMode.title),
                cx, 50, displayColor);
        graphics.centeredText(this.font, Component.literal(gameMode.description),
                cx, 64, withAlpha(0x888888, titleAlpha));

        // Divider
        int divAlpha = (int)(introAlpha * 80);
        graphics.fill(cx - 100, 76, cx + 100, 77, withAlpha(0xFFFFFF, divAlpha));

        // Entity preview
        if (previewEntity != null) {
            float maxDim = Math.max(previewEntity.getBbHeight(), previewEntity.getBbWidth());
            int size = Math.max(12, (int)(40f / Math.max(1.5f, maxDim)));
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, cx - size, 80, cx + size, 80 + size * 2,
                        size, 0.0625f, mouseX, mouseY, previewEntity);
            } catch (Exception ignored) {}
        }

        // ── Mode cards ───────────────────────────────────────────────────────
        int cardW = 160;
        int cardH = 80;
        int cardY = this.height / 2 + 10;
        int gap   = 20;
        int soloX  = (gameMode == GameMode.ROULETTE || gameMode == GameMode.MOB_BRAWL) ? cx - cardW / 2 : cx - cardW - gap / 2;
        int multiX = gameMode == GameMode.MOB_BRAWL ? cx - cardW / 2 : cx + gap / 2;

        // Hover detection
        boolean soloHovered  = mouseX >= soloX  && mouseX < soloX  + cardW && mouseY >= cardY && mouseY < cardY + cardH;
        boolean multiHovered = mouseX >= multiX && mouseX < multiX + cardW && mouseY >= cardY && mouseY < cardY + cardH;

        soloHover  += (( soloHovered ? 1f : 0f) - soloHover)  * 0.2f;
        multiHover += ((multiHovered ? 1f : 0f) - multiHover) * 0.2f;

        // Show multiplayer card for all except Roulette
        if (gameMode != GameMode.ROULETTE) {
            renderModeCard(graphics, multiX, cardY, cardW, cardH, multiHover,
                    "👥 Multiplayer", "Race against others\non the same server.",
                    gameMode == GameMode.MOB_BRAWL ? 0xFFFF5555 : 0xFF5599FF, false);
        }

        // Show solo card for all except Mob Brawl
        if (gameMode != GameMode.MOB_BRAWL) {
            renderModeCard(graphics, soloX, cardY, cardW, cardH, soloHover,
                    "🎮 Solo", "Play alone at your own pace.", 0xFF55FF55, false);
        }

        // ── Stats button — Roulette only ──────────────────────────────────────
        if (gameMode == GameMode.ROULETTE) {
            int sbW = 80, sbH = 20;
            int sbX = cx - sbW / 2;
            int sbY = cardY + cardH + 8;
            boolean sbHov = mouseX >= sbX && mouseX < sbX + sbW && mouseY >= sbY && mouseY < sbY + sbH;
            float sbPulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
            int sbAccent = withAlpha(0x5599FF, (int)((sbHov ? 0.8f : 0.4f + sbPulse * 0.2f) * 200));
            graphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, withAlpha(0x0A0A18, 200));
            graphics.fill(sbX,           sbY,           sbX + sbW, sbY + 1,      sbAccent);
            graphics.fill(sbX,           sbY + sbH - 1, sbX + sbW, sbY + sbH,    sbAccent);
            graphics.fill(sbX,           sbY,           sbX + 1,   sbY + sbH,    sbAccent);
            graphics.fill(sbX + sbW - 1, sbY,           sbX + sbW, sbY + sbH,    sbAccent);
            graphics.centeredText(this.font, Component.literal("📊 Stats"), cx, sbY + 6, sbAccent);
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(),
                    sbHov ? GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR) : 0);
        }

        // "Select a mode" hint
        graphics.centeredText(this.font, Component.literal("§7Select a mode to play"),
                cx, cardY - 14, withAlpha(0x666666, titleAlpha));

        // Hand cursor when hovering over cards
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(),
                (soloHovered || multiHovered) ? handCursor : 0);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderModeCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                float hover, String title, String desc, int accentColor, @SuppressWarnings("SameParameterValue") boolean comingSoon) {
        int bg     = lerpColor(0xFF111122, 0xFF1E1E35, hover);
        int border = comingSoon ? lerpColor(0xFF333344, 0xFF5566AA, hover) : lerpColor(0xFF2A3A2A, accentColor, hover);

        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x,       y,       x + w,   y + 1,   border);
        graphics.fill(x,       y + h-1, x + w,   y + h,   border);
        graphics.fill(x,       y,       x + 1,   y + h,   border);
        graphics.fill(x + w-1, y,       x + w,   y + h,   border);

        // Inner glow on hover
        if (hover > 0.05f) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(border & 0x00FFFFFF, (int)(hover * 60)));
        }

        int titleColor = comingSoon ? lerpColor(0xFF555566, 0xFF8888CC, hover) : lerpColor(0xFFCCCCCC, accentColor, hover);
        graphics.centeredText(this.font, Component.literal(title), x + w / 2, y + 12, titleColor);

        // Divider
        graphics.fill(x + 20, y + 24, x + w - 20, y + 25, withAlpha(border & 0x00FFFFFF, (int)(hover * 80 + 30)));

        // Description lines
        int descY = y + 30;
        for (String line : desc.split("\n")) {
            graphics.centeredText(this.font, Component.literal(line), x + w / 2, descY,
                    comingSoon ? 0xFF444455 : 0xFF888888);
            descY += 11;
        }

        // Coming Soon badge or Play hint
        if (comingSoon) {
            graphics.centeredText(this.font, Component.literal("§8— Coming Soon —"), x + w / 2, y + h - 16, 0xFF333344);
        } else {
            int playAlpha = (int)(hover * 200 + 30);
            graphics.centeredText(this.font, Component.literal("▶ Play"),
                    x + w / 2, y + h - 16, withAlpha(accentColor & 0x00FFFFFF, playAlpha));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        int cardW  = 160;
        int cardH  = 80;
        int cardY  = this.height / 2 + 10;
        int cx     = this.width / 2;
        int gap    = 20;
        int soloX  = (gameMode == GameMode.ROULETTE || gameMode == GameMode.MOB_BRAWL) ? cx - cardW / 2 : cx - cardW - gap / 2;

        if (gameMode != GameMode.ROULETTE) {
            //noinspection DuplicateExpressions
            int multiX = gameMode == GameMode.MOB_BRAWL ? cx - cardW / 2 : cx + gap / 2;
            if (event.x() >= multiX && event.x() < multiX + cardW
                    && event.y() >= cardY && event.y() < cardY + cardH) {
                playClick();
                GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0);
                Minecraft.getInstance().setScreen(new RoomBrowserScreen(gameMode));
                return true;
            }
        }

        // Solo card click
        if (gameMode != GameMode.MOB_BRAWL && event.x() >= soloX && event.x() < soloX + cardW
                && event.y() >= cardY && event.y() < cardY + cardH) {
            playClick();
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0);
            switch (gameMode) {
                case TRIVIA     -> Minecraft.getInstance().setScreen(new MorphTriviaScreen());
                case ROULETTE   -> Minecraft.getInstance().setScreen(new net.naw.morphling.client.games.MorphRoulette.MorphRouletteConfigScreen());
            }
            return true;
        }

        // Multiplayer card click
        //noinspection DuplicateExpressions
        int multiX = gameMode == GameMode.MOB_BRAWL ? cx - cardW / 2 : cx + gap / 2;
        if (event.x() >= multiX && event.x() < multiX + cardW
                && event.y() >= cardY && event.y() < cardY + cardH) {
            playClick();
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0);
            Minecraft.getInstance().setScreen(new RoomBrowserScreen(gameMode));
            return true;
        }

        if (gameMode == GameMode.ROULETTE) {
            int sbW = 80, sbH = 20;
            int sbX = cx - sbW / 2;
            int sbY = cardY + cardH + 8;
            if (event.x() >= sbX && event.x() < sbX + sbW && event.y() >= sbY && event.y() < sbY + sbH) {
                playClick();
                Minecraft.getInstance().setScreen(new net.naw.morphling.client.games.MorphRoulette.MorphRouletteStatsScreen());
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void playClick() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3f, 1.2f, false);
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.05f, 0.9f, false);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int)(ar + (br - ar) * t) << 16)
                | ((int)(ag + (bg - ag) * t) << 8)
                |  (int)(ab + (bb - ab) * t);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public void onClose() {
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0); // reset to default first
        if (handCursor != 0L) {
            GLFW.glfwDestroyCursor(handCursor);
            handCursor = 0L;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}