package net.naw.morphling.client.games.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NonNull;

/**
 * Intro screen shown before MorphGamesScreen.

 * Draws a static fake version of the games menu in the background (darkened),
 * each card spotlights one by one with a sound, then all brighten together,
 * title fades in, transitions to real menu.

 * Flow:
 *   0.0 - 2.4s : Cards spotlight one by one (6 cards x 0.4s each)
 *   2.4 - 2.8s : All cards brighten together
 *   0.0 - 1.3s : Title fades in simultaneously
 *   2.8 - 3.2s : Hold
 *   3.2 - 3.8s : Fade to black, open real menu
 */
public class MorphGamesIntroScreen extends Screen {

    private static final float WIPE_END  = 0.0f;
    private static final float TITLE_END = 1.3f;
    private static final float HOLD_END  = 3.2f;
    private static final float FADE_END  = 3.8f;

    // Same card layout constants as MorphGamesScreen
    private static final int CARD_HEIGHT_COLLAPSED = 90;
    private static final int CARD_SPACING          = 12;
    private static final int COLUMNS               = 3;

    private static final int[] ACCENT_COLORS = {
            0xFF55FF55, 0xFFFF5555, 0xFFFFAA00,
            0xFF5599FF, 0xFFCC55FF, 0xFF55FFCC,
    };
    private static final String[] TITLES = {
            "🙈 Hide & Seek", "⚔ Mob Brawl", "🏆 Hunger Games",
            "🎲 Morph Roulette", "🧠 Morph Trivia", "🎯 Morph Hunt",
    };

    private float   timer   = 0f;
    private float   hue     = 0f;
    private boolean skipped = false;

    // Sound flag per card — plays once when spotlight peaks
    private final boolean[] playedCardSound = new boolean[6];

    public MorphGamesIntroScreen() {
        super(Component.literal(""));
    }

    @Override
    protected void init() {}

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        timer += dt;
        hue    = (hue + dt * 0.5f) % 1.0f;

        if (timer >= FADE_END) { advance(); return; }

        int cx = this.width / 2;

        // ── Draw fake menu background ─────────────────────────────────────────
        drawFakeMenu(graphics, timer);

        // ── Title fade in ─────────────────────────────────────────────────────
        if (timer >= WIPE_END && timer < FADE_END) {
            float titleP  = Math.min(1f, (timer - WIPE_END) / (TITLE_END - WIPE_END));
            float fadeOut = timer > HOLD_END
                    ? 1f - (timer - HOLD_END) / (FADE_END - HOLD_END)
                    : 1f;

            // Dim overlay so title pops
            int dimAlpha = (int)((1f - (timer > HOLD_END ? (timer - HOLD_END) / (FADE_END - HOLD_END) : 0f)) * titleP * 100);
            graphics.fill(0, 0, this.width, this.height, (dimAlpha << 24));

            int rgb        = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
            int titleColor = withAlpha(rgb & 0x00FFFFFF, (int)(titleP * fadeOut * 255));
            graphics.centeredText(this.font, Component.literal("✦ Morph Games ✦"), cx, this.height / 25, titleColor);
        }


        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Draws a static approximation of the MorphGamesScreen card grid */
    private void drawFakeMenu(GuiGraphicsExtractor graphics, float t) {
        int cardW        = (this.width - 20 - CARD_SPACING * (COLUMNS - 1)) / COLUMNS;
        int startX       = (this.width - (cardW * COLUMNS + CARD_SPACING * (COLUMNS - 1))) / 2;
        int topAnchor    = 52;
        int bottomAnchor = this.height - 10;

        // Title bar brightness based on time
        float titleB = Math.min(1f, t / 0.3f) * 0.3f;
        graphics.centeredText(this.font, Component.literal("Pick a game mode"),
                this.width / 2, 32, withAlpha(0x888888, (int)(titleB * 200)));

        String[] descs = {
                "Blend in with real mobs.\nDon't get found.",
                "1v1 arena. Abilities only.",
                "Last morph standing wins.",
                "Random morph every 30s.\nSurvive.",
                "Guess the morph from\nits ability description.",
                "Hunt mobs. Absorb their form.\nCollect them all.",
        };
        String[] badges = {
                "👥 Multiplayer", "👥 Multiplayer", "👥 Multiplayer",
                "🎮 Solo", "🎮 Solo / 👥 Multi", "🎮 Solo"
        };

        for (int i = 0; i < 6; i++) {
            int col   = i % COLUMNS;
            int row   = i / COLUMNS;
            int x     = startX + col * (cardW + CARD_SPACING);
            int cardH = CARD_HEIGHT_COLLAPSED;
            int y     = row == 0 ? topAnchor : bottomAnchor - cardH;
            int accent = ACCENT_COLORS[i];

            float cardDelay = i * 0.4f;
            float cardP     = Math.clamp((t - cardDelay) / 0.3f, 0f, 1f);
            float spotlight = (float)Math.exp(-Math.pow((t - cardDelay - 0.3f) * 4f, 2));
            float allDone   = Math.clamp((t - 6 * 0.4f) / 0.4f, 0f, 1f);
            float brightness = Math.max(Math.max(spotlight, cardP * 0.15f), allDone);

            // Play sound when this card's spotlight peaks
            if (spotlight > 0.5f && !playedCardSound[i]) {
                playedCardSound[i] = true;
                playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f + i * 0.1f);
            }

            graphics.fill(x, y, x + cardW, y + cardH, withAlpha(0x1A1A1A, (int)(brightness * 0xFF)));

            int borderColor = withAlpha(accent & 0x00FFFFFF, (int)(brightness * 80));
            graphics.fill(x,           y,           x + cardW,   y + 1,      borderColor);
            graphics.fill(x,           y + cardH-1, x + cardW,   y + cardH,  borderColor);
            graphics.fill(x,           y,           x + 1,       y + cardH,  borderColor);
            graphics.fill(x + cardW-1, y,           x + cardW,   y + cardH,  borderColor);

            graphics.centeredText(this.font, Component.literal(TITLES[i]),
                    x + cardW / 2,
                    row == 0 ? y + 8 : y + cardH - 15,
                    withAlpha(accent & 0x00FFFFFF, (int)(brightness * 180)));

            int maxW = cardW - 16;
            String[] descLines = descs[i].split("\n");
            int descY = row == 0 ? y + 22 : y + cardH - 40;
            for (String rawLine : descLines) {
                String remaining = rawLine;
                while (this.font.width(remaining) > maxW) {
                    int cut = remaining.length() - 1;
                    while (cut > 0 && this.font.width(remaining.substring(0, cut)) > maxW) cut--;
                    int space = remaining.lastIndexOf(' ', cut);
                    if (space > 0) cut = space;
                    graphics.centeredText(this.font, Component.literal(remaining.substring(0, cut).trim()),
                            x + cardW / 2, descY, withAlpha(0xAAAAAA, (int)(brightness * 160)));
                    descY += 10;
                    remaining = remaining.substring(cut).trim();
                }
                graphics.centeredText(this.font, Component.literal(remaining),
                        x + cardW / 2, descY, withAlpha(0xAAAAAA, (int)(brightness * 160)));
                descY += 10;
            }

            graphics.centeredText(this.font, Component.literal(badges[i]),
                    x + cardW / 2,
                    row == 0 ? y + cardH - 14 : y + 10,
                    withAlpha(0x555555, (int)(brightness * 160)));
        }
    }

    private void playSound(SoundEvent sound, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    sound, SoundSource.MASTER, (float) 0.05, pitch, false);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    private void advance() {
        if (!skipped) {
            skipped = true;
            Minecraft.getInstance().setScreen(new MorphGamesScreen());
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}