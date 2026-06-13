package net.naw.morphling.client.games.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.ui.MorphMenuScreen;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Morph Games screen — launched from the RGB button in MorphMenuScreen.

 * Layout:
 *   - Title + subtitle at top (rainbow animated)
 *   - 6 game mode cards in a 3x2 grid
 *   - Top row (0-2): cards expand DOWNWARD on hover
 *   - Bottom row (3-5): cards expand UPWARD on hover (flipped layout)
 *   - Card width scales dynamically to fit any GUI scale

 * Card content (top row, top→bottom):
 *   Title → Description → Divider → Entity scene → Coming Soon

 * Card content (bottom row, bottom→top — mirrored):
 *   Title → Description → Divider → Entity scene → Coming Soon

 * Z-order:
 *   - Hovering top row: top row renders on top of bottom row
 *   - Hovering bottom row: bottom row renders on top of top row

 * Each card has:
 *   - Unique accent color
 *   - Animated entity scene (card-specific, plays on expand)
 *   - Hover sound on mouse enter
 *   - Play button (appears at p > 0.8, with hand cursor + hover highlight)
 *   - Smooth expand/collapse animation (hoverProgress 0→1)
 */
public class MorphGamesScreen extends Screen {

    // ── Layout constants ────────────────────────────────────────────────────
    private static final int CARD_HEIGHT_COLLAPSED = 90;   // height when not hovered
    private static final int CARD_HEIGHT_EXPANDED  = 180;  // max height when fully hovered
    private static final int CARD_SPACING          = 12;   // gap between cards
    private static final int COLUMNS               = 3;    // cards per row

    private static net.minecraft.client.resources.sounds.SimpleSoundInstance discSound = null;
    private static boolean discStarted = false;

    // ── Game mode data ───────────────────────────────────────────────────────
    // Index order: 0=Hide&Seek, 1=MobBrawl, 2=HungerGames, 3=Roulette, 4=Trivia, 5=MorphHunt

    /** Short description shown when card is collapsed */
    private static final String[] SHORT_DESC = {
            "Blend in with real mobs.\nDon't get found.",
            "1v1 arena. Abilities only.",
            "Last morph standing wins.",
            "Random morph every 30s.\nSurvive.",
            "Guess the morph from\nits ability description.",
            "Hunt mobs. Absorb their form.\nCollect them all.",
    };

    /** Long description shown when card is expanded (p > 0.5) */
    private static final String[] EXPANDED_DESC = {
            "Morph into a mob and blend in\nwith the real ones. The seeker\nmust find you before time runs out.",
            "Choose your morph. Master your\nabilities. The arena has no rules —\nonly winners.",
            "Every morph has strengths and\nweaknesses. Use them wisely.\nLast one standing wins.",
            "You never know what you'll be\nnext. Adapt fast or die faster.\nChaos is the only strategy.",
            "Read the ability description.\nGuess the morph. First to answer\ncorrectly wins the round.",
            "Kill a mob to absorb its morph.\nExplore the world, hunt every\nspecies, become them all.",
    };

    /** Unique accent color per card (border, title, divider, play button) */
    private static final int[] ACCENT_COLORS = {
            0xFF55FF55, // 0: Hide & Seek     — green
            0xFFFF5555, // 1: Mob Brawl        — red
            0xFFFFAA00, // 2: Hunger Games     — orange
            0xFF5599FF, // 3: Morph Roulette   — blue
            0xFFCC55FF, // 4: Morph Trivia     — purple
            0xFF55FFCC, // 5: Morph Hunt       — teal
    };

    /** Card titles (shown at top of top-row cards, bottom of bottom-row cards) */
    private static final String[] TITLES = {
            "🙈 Hide & Seek", "⚔ Mob Brawl", "🏆 Hunger Games",
            "🎲 Morph Roulette", "🧠 Morph Trivia", "🎯 Morph Hunt",
    };

    // ── Per-card animation state ─────────────────────────────────────────────

    /** Hover expand progress per card: 0.0 = collapsed, 1.0 = fully expanded */
    private final float[] hoverProgress = new float[6];

    /** Tracks previous hover state to detect mouse-enter for sound trigger */
    private final boolean[] wasHovered = new boolean[6];

    /** Per-card scene animation timer (increments every frame) */
    private final float[] sceneTimer = new float[6];

    // ── Entity scene state ───────────────────────────────────────────────────

    /** Cached living entities used for animated scenes inside each card */
    @SuppressWarnings("unchecked")
    private final List<LivingEntity>[] sceneEntities = new List[6];

    /** Roulette card (index 3): current mob index cycling through ROULETTE_MOBS */
    private int rouletteIndex = 0;

    /** Roulette card: time since last swap — swaps every 1.5s */
    private float rouletteTimer = 0f;

    /** Mob cycle order for the Roulette card scene */
    private static final EntityType<?>[] ROULETTE_MOBS = {
            EntityType.CHICKEN, EntityType.WOLF, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.PARROT, EntityType.SLIME
    };

    /**
     * Morph Hunt card (index 5): mobs cycle one by one to show the variety you can hunt.
     * A new mob fades in every 1.2s — gives the feel of a living world full of prey.
     */
    private static final EntityType<?>[] HUNT_MOBS = {
            EntityType.CREEPER, EntityType.ENDERMAN, EntityType.SKELETON,
            EntityType.ZOMBIE, EntityType.SPIDER, EntityType.BLAZE,
            EntityType.WITCH, EntityType.IRON_GOLEM, EntityType.SLIME,
    };
    private int   huntIndex = 0;
    private float huntTimer = 0f;

    // ── Title rainbow animation ──────────────────────────────────────────────

    /** Hue value cycling 0→1 for the rainbow title color */
    private float hue = 0f;

    // ── Constructor ──────────────────────────────────────────────────────────

    public MorphGamesScreen() {
        super(Component.literal("Morph Games"));
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Back button — returns to morph menu
        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> { this.onClose(); Minecraft.getInstance().setScreen(new MorphMenuScreen()); }
        ).bounds(10, 10, 60, 20).build());

        // Play chirp disc when games screen opens — only once
        if (!discStarted) {
            discStarted = true;
            discSound = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                    net.minecraft.sounds.SoundEvents.MUSIC_DISC_CHIRP.value().location(),
                    net.minecraft.sounds.SoundSource.RECORDS,
                    0.1f, 0.9f, net.minecraft.util.RandomSource.create(), false, 0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                    0, 0, 0, true);
            Minecraft.getInstance().getSoundManager().play(discSound);
        }

        var level = Minecraft.getInstance().level;
        if (level == null) return;

        for (int i = 0; i < 6; i++) sceneEntities[i] = new ArrayList<>();

        // Card 0: Hide & Seek — 3 chickens + 1 wolf patrolling
        spawn(0, EntityType.CHICKEN, level);
        spawn(0, EntityType.CHICKEN, level);
        spawn(0, EntityType.CHICKEN, level);
        spawn(0, EntityType.WOLF, level);

        // Card 1: Mob Brawl — wolf vs creeper facing each other
        spawn(1, EntityType.WOLF, level);
        spawn(1, EntityType.CREEPER, level);

        // Card 2: Hunger Games — 4 mobs rotating in a circle
        spawn(2, EntityType.CHICKEN, level);
        spawn(2, EntityType.SHEEP, level);
        spawn(2, EntityType.ZOMBIE, level);
        spawn(2, EntityType.SKELETON, level);

        // Card 3: Morph Roulette — single entity that swaps every 1.5s
        spawn(3, ROULETTE_MOBS[0], level);

        // Card 4: Morph Trivia — villager with floating question marks
        spawn(4, EntityType.VILLAGER, level);

        // Card 5: Morph Hunt — single mob that cycles through hunt targets every 1.2s
        // gives the feel of a world full of different prey to absorb
        spawn(5, HUNT_MOBS[0], level);
    }

    /** Creates a LivingEntity of the given type and adds it to the card's scene list */
    private void spawn(int card, EntityType<?> type, net.minecraft.world.level.Level level) {
        var e = type.create(level, EntitySpawnReason.LOAD);
        if (e instanceof LivingEntity le) sceneEntities[card].add(le);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // ── Rainbow title ────────────────────────────────────────────────────
        // hue cycles slowly; change 0.001f to speed up or slow down
        hue = (hue + 0.001f) % 1.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        int rainbowColor = 0xFF000000 | ((rgb >> 16 & 0xFF) << 16) | ((rgb >> 8 & 0xFF) << 8) | (rgb & 0xFF);

        graphics.centeredText(this.font, Component.literal("✦ Morph Games ✦"), this.width / 2, 18, rainbowColor);
        graphics.centeredText(this.font, Component.literal("Pick a game mode"), this.width / 2, 32, 0xFF888888);

        // ── BETA stamp — top-right corner ────────────────────────────────────
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width - 24f, 18f);
        graphics.pose().rotate((float) Math.toRadians(15));
        graphics.centeredText(this.font, Component.literal("BETA"), 0, 0, 0xFFFF4444);
        graphics.pose().popMatrix();

        // ── Timers ───────────────────────────────────────────────────────────
        float dt = 0.016f; // ~60fps tick; used for scene animations
        for (int i = 0; i < 6; i++) sceneTimer[i] += dt;

        // Roulette swap: every 1.5 seconds swap the entity in card 3
        rouletteTimer += dt;
        if (rouletteTimer > 1.5f) {
            rouletteTimer = 0f;
            rouletteIndex = (rouletteIndex + 1) % ROULETTE_MOBS.length;
            var level = Minecraft.getInstance().level;
            if (level != null && sceneEntities[3] != null && !sceneEntities[3].isEmpty()) {
                sceneEntities[3].clear();
                spawn(3, ROULETTE_MOBS[rouletteIndex], level);
            }
        }

        // Morph Hunt swap: every 1.2s cycle through HUNT_MOBS to show variety of prey
        huntTimer += dt;
        if (huntTimer > 2.0f) {
            huntTimer = 0f;
            huntIndex = (huntIndex + 1) % HUNT_MOBS.length;
            var level = Minecraft.getInstance().level;
            if (level != null && sceneEntities[5] != null && !sceneEntities[5].isEmpty()) {
                sceneEntities[5].clear();
                spawn(5, HUNT_MOBS[huntIndex], level);
            }
        }

        // ── Grid layout ──────────────────────────────────────────────────────
        // cardW scales with screen width so cards always fit any GUI scale
        int cardW = (this.width - 20 - CARD_SPACING * (COLUMNS - 1)) / COLUMNS;
        int startX = (this.width - (cardW * COLUMNS + CARD_SPACING * (COLUMNS - 1))) / 2;

        int topRowAnchor    = 52;               // top edge of top row (cards grow downward)
        int bottomRowAnchor = this.height - 10; // bottom edge of bottom row (cards grow upward)

        // ── Z-order: hovered row renders last (on top) ───────────────────────
        boolean hoveringBottom = false;
        for (int i = 3; i < 6; i++) {
            if (hoverProgress[i] > 0.3f) { hoveringBottom = true; break; }
        }
        // Bottom row drawn first → top row on top (default)
        // Top row drawn first → bottom row on top (when bottom hovered)
        int[] renderOrder = hoveringBottom
                ? new int[]{0, 1, 2, 3, 4, 5}
                : new int[]{3, 4, 5, 0, 1, 2};

        // ── Card loop ────────────────────────────────────────────────────────
        for (int idx = 0; idx < 6; idx++) {
            int i          = renderOrder[idx];
            int col        = i % COLUMNS;
            int row        = i / COLUMNS;
            int x          = startX + col * (cardW + CARD_SPACING);
            boolean isBottomRow = row == 1;

            // Expand/collapse animation
            float p     = hoverProgress[i];
            int   cardH = CARD_HEIGHT_COLLAPSED + (int)(p * (CARD_HEIGHT_EXPANDED - CARD_HEIGHT_COLLAPSED));

            // Top row: anchored at top, grows down. Bottom row: anchored at bottom, grows up.
            int y = isBottomRow ? bottomRowAnchor - cardH : topRowAnchor;

            // Hover detection
            boolean hovered = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH;
            // If this is a bottom row card, block hover if the top card in same column is expanded
            if (isBottomRow && hoverProgress[i - 3] > 0.3f) hovered = false;
            // If this is a top row card, block hover if the bottom card in same column is expanded
            if (!isBottomRow && hoverProgress[i + 3] > 0.3f && mouseY > y + CARD_HEIGHT_COLLAPSED) hovered = false;

            // Play hover-enter sound
            if (hovered && !wasHovered[i]) {
                var mc = Minecraft.getInstance();
                if (mc.level != null && mc.player != null) {
                    mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.15f, 1.4f, false);
                }
            }
            wasHovered[i] = hovered;

            // Smooth lerp toward target (1 if hovered, 0 if not)
            float target = hovered ? 1f : 0f;
            hoverProgress[i] += (target - hoverProgress[i]) * 0.15f;
            if (hoverProgress[i] < 0.001f) hoverProgress[i] = 0f;
            if (hoverProgress[i] > 0.999f) hoverProgress[i] = 1f;

            int accentColor = ACCENT_COLORS[i];
            int maxTextWidth = cardW - 16; // text wraps within card minus padding

            // ── Card background ──────────────────────────────────────────────
            graphics.fill(x, y, x + cardW, y + cardH, lerpColor(0xFF1A1A1A, 0xFF252525, p));

            // ── Card border (accent color on hover, dim otherwise) ────────────
            int borderColor = hovered ? accentColor : (accentColor & 0x00FFFFFF) | 0x88000000;
            graphics.fill(x,           y,           x + cardW,     y + 1,      borderColor);
            graphics.fill(x,           y + cardH-1, x + cardW,     y + cardH,  borderColor);
            graphics.fill(x,           y,           x + 1,         y + cardH,  borderColor);
            graphics.fill(x + cardW-1, y,           x + cardW,     y + cardH,  borderColor);
            // Inner glow lines on hover
            if (hovered) {
                graphics.fill(x+1, y+1,       x+cardW-1, y+2,       lerpColor(accentColor, 0x00000000, 0.5f));
                graphics.fill(x+1, y+cardH-2, x+cardW-1, y+cardH-1, lerpColor(accentColor, 0x00000000, 0.5f));
            }

            int titleColor = lerpColor(accentColor & 0xFFCCCCCC, accentColor, p);

            // ── Bottom row layout (flipped — everything renders bottom→up) ────
            if (isBottomRow) {

                // Mode badge at top of card (y + 10)
                graphics.centeredText(this.font, Component.literal(
                                new String[]{"👥 Multiplayer", "👥 Multiplayer", "👥 Multiplayer", "🎮 Solo", "🎮 Solo / 👥 Multi", "🎮 Solo"}[i]),
                        x + cardW / 2, y + 10, lerpColor(0xFF555555, accentColor & 0x00FFFFFF | 0x88000000, p));

                // Entity scene — upper portion of card (only visible when expanded)
                if (p > 0.1f && sceneEntities[i] != null && !sceneEntities[i].isEmpty()) {
                    int sceneTop    = y + 40;
                    int sceneBottom = y + cardH - CARD_HEIGHT_COLLAPSED + 1;
                    if (sceneBottom - sceneTop > 8) {
                        graphics.enableScissor(x + 2, sceneTop, x + cardW - 2, sceneBottom);
                        renderScene(graphics, i, x, cardW, sceneTop, sceneBottom, mouseX, mouseY, p);
                        graphics.disableScissor();
                    }
                }

                // Description — word-wrapped, rendered bottom→up above title
                String desc = p > 0.5f ? EXPANDED_DESC[i] : SHORT_DESC[i];
                List<String> wrappedLines = wrapText(desc, maxTextWidth);

                // Title sits at the very bottom of the card
                int titleY = y + cardH - 15;
                graphics.centeredText(this.font, Component.literal(TITLES[i]), x + cardW / 2, titleY, titleColor);

                // Description lines render upward from title
                int descBottomY = titleY - 4;
                for (int l = wrappedLines.size() - 1; l >= 0; l--) {
                    descBottomY -= 10;
                    graphics.centeredText(this.font, Component.literal(wrappedLines.get(l)), x + cardW / 2, descBottomY, 0xFFAAAAAA);
                }

                // Divider line — sits just above the description block
                if (p > 0.3f) {
                    int dividerAlpha = (int)(p * 100);
                    graphics.fill(x + 20, descBottomY - 4, x + cardW - 20, descBottomY - 3,
                            (dividerAlpha << 24) | (accentColor & 0x00FFFFFF));
                }

                // Play button — fades in when nearly fully expanded (p > 0.8)
                if (p > 0.8f) {
                    int btnW = 60, btnH = 14;
                    int btnX = x + cardW / 2 - btnW / 2;
                    int btnY = descBottomY - 22;
                    boolean isPlayable = (i != 0 && i != 2 && i != 5);
                    boolean btnHovered = isPlayable && mouseX >= btnX && mouseX < btnX + btnW
                            && mouseY >= btnY && mouseY < btnY + btnH;
                    GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(),
                            btnHovered ? GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR) : 0);
                    if (isPlayable) {
                        int btnColor = btnHovered
                                ? lerpColor(accentColor, 0xFFFFFFFF, 0.3f)
                                : lerpColor(0xFF333333, accentColor, (p - 0.8f) * 5f);
                        graphics.fill(btnX,        btnY,        btnX+btnW, btnY+btnH,   btnColor);
                        graphics.fill(btnX,        btnY,        btnX+btnW, btnY+1,      accentColor);
                        graphics.fill(btnX,        btnY+btnH-1, btnX+btnW, btnY+btnH,   accentColor);
                        graphics.fill(btnX,        btnY,        btnX+1,    btnY+btnH,   accentColor);
                        graphics.fill(btnX+btnW-1, btnY,        btnX+btnW, btnY+btnH,   accentColor);
                        graphics.centeredText(this.font, Component.literal("▶ Play"), x + cardW / 2, btnY + 3, 0xFFFFFFFF);
                    } else {
                        graphics.centeredText(this.font,
                                Component.literal("§8— Coming Soon —"),
                                x + cardW / 2, btnY + 3, 0xFF444455);
                    }
                }

                // ── Top row layout (normal — everything renders top→down) ─────────
            } else {

                // Title at top of card (y + 8)
                graphics.centeredText(this.font, Component.literal(TITLES[i]), x + cardW / 2, y + 8, titleColor);

                // Description — word-wrapped, renders downward from title
                String desc = p > 0.5f ? EXPANDED_DESC[i] : SHORT_DESC[i];
                int descY = y + 22;
                for (String line : desc.split("\n")) {
                    while (this.font.width(line) > maxTextWidth) {
                        int cutAt = line.length() - 1;
                        while (cutAt > 0 && this.font.width(line.substring(0, cutAt)) > maxTextWidth) cutAt--;
                        int spaceAt = line.lastIndexOf(' ', cutAt);
                        if (spaceAt > 0) cutAt = spaceAt;
                        graphics.centeredText(this.font, Component.literal(line.substring(0, cutAt).trim()), x + cardW / 2, descY, 0xFFAAAAAA);
                        descY += 10;
                        line = line.substring(cutAt).trim();
                    }
                    graphics.centeredText(this.font, Component.literal(line), x + cardW / 2, descY, 0xFFAAAAAA);
                    descY += 10;
                }

                // Divider line — sits just below description, above scene
                if (p > 0.3f) {
                    int dividerAlpha = (int)(p * 100);
                    graphics.fill(x + 20, descY + 3, x + cardW - 20, descY + 4,
                            (dividerAlpha << 24) | (accentColor & 0x00FFFFFF));
                }

                // Entity scene — lower portion of card (only visible when expanded)
                if (p > 0.1f && sceneEntities[i] != null && !sceneEntities[i].isEmpty()) {
                    int sceneTop    = y + CARD_HEIGHT_COLLAPSED + 4;
                    int sceneBottom = y + cardH - 30;
                    if (sceneBottom - sceneTop > 8) {
                        graphics.enableScissor(x + 2, sceneTop, x + cardW - 2, sceneBottom);
                        renderScene(graphics, i, x, cardW, sceneTop, sceneBottom, mouseX, mouseY, p);
                        graphics.disableScissor();
                    }
                }

                // Play button — fades in when nearly fully expanded (p > 0.8)
                if (p > 0.8f) {
                    int btnW = 60, btnH = 14;
                    int btnX = x + cardW / 2 - btnW / 2;
                    int btnY = descY + 8;
                    boolean isPlayable = (i != 0 && i != 2 && i != 5);
                    boolean btnHovered = isPlayable && mouseX >= btnX && mouseX < btnX + btnW
                            && mouseY >= btnY && mouseY < btnY + btnH;
                    GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(),
                            btnHovered ? GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR) : 0);
                    if (isPlayable) {
                        int btnColor = btnHovered
                                ? lerpColor(accentColor, 0xFFFFFFFF, 0.3f)
                                : lerpColor(0xFF333333, accentColor, (p - 0.8f) * 5f);
                        graphics.fill(btnX,        btnY,        btnX+btnW, btnY+btnH,   btnColor);
                        graphics.fill(btnX,        btnY,        btnX+btnW, btnY+1,      accentColor);
                        graphics.fill(btnX,        btnY+btnH-1, btnX+btnW, btnY+btnH,   accentColor);
                        graphics.fill(btnX,        btnY,        btnX+1,    btnY+btnH,   accentColor);
                        graphics.fill(btnX+btnW-1, btnY,        btnX+btnW, btnY+btnH,   accentColor);
                        graphics.centeredText(this.font, Component.literal("▶ Play"), x + cardW / 2, btnY + 3, 0xFFFFFFFF);
                    } else {
                        graphics.centeredText(this.font,
                                Component.literal("§8— Coming Soon —"),
                                x + cardW / 2, btnY + 3, 0xFF444455);
                    }
                }

                // Mode badge at bottom of card
                graphics.centeredText(this.font, Component.literal(
                                new String[]{"👥 Multiplayer", "👥 Multiplayer", "👥 Multiplayer", "🎮 Solo", "🎮 Solo / 👥 Multi", "🎮 Solo"}[i]),
                        x + cardW / 2, y + cardH - 14, lerpColor(0xFF555555, accentColor & 0x00FFFFFF | 0x88000000, p));
            }
        }

        for (int i = 0; i < 3; i++) {
            if (hoverProgress[i + 3] > 0.3f) {
                hoverProgress[i] = Math.max(0f, hoverProgress[i] - 0.15f);
                wasHovered[i] = true;
            }
        }
    }

    // ── Scene renderer ───────────────────────────────────────────────────────

    /**
     * Renders the animated entity scene for a given card.
     * Each card has a unique story told through entity movement.
     * sceneTop/sceneBottom define the vertical clip region for the scene.
     */
    private void renderScene(@NonNull GuiGraphicsExtractor graphics, int card, int cardX, int cardW,
                             int sceneTop, int sceneBottom, int mouseX, int mouseY, float p) {
        List<LivingEntity> entities = sceneEntities[card];
        if (entities.isEmpty()) return;

        float t        = sceneTimer[card];
        int   midX     = cardX + cardW / 2;
        int   midY     = sceneBottom - 4;
        int   entitySize = (int)(18 * p);
        if (entitySize < 4) return;

        switch (card) {
            case 0 -> {
                // Hide & Seek: 3 chickens standing still, wolf paces left-right scanning
                int[] offsets = {-40, 0, 38};
                for (int c = 0; c < 3 && c < entities.size() - 1; c++)
                    renderEntity(graphics, entities.get(c), midX + offsets[c], midY, entitySize - 4, mouseX, mouseY);
                // Wolf pacing speed: change 0.8f. Range: change 35
                int wolfX = midX + (int)(Math.sin(t * 0.8f) * 35);
                if (entities.size() > 3) renderEntity(graphics, entities.get(3), wolfX, midY, entitySize, mouseX, mouseY);
            }
            case 1 -> {
                // Mob Brawl: wolf and creeper bounce toward each other, lightning flashes between
                if (entities.size() >= 2) {
                    // Bounce speed: change 3f. Bounce range: change 5
                    int wolfX    = midX - 30 + (int)(Math.abs(Math.sin(t * 3f)) * 5);
                    int creeperX = midX + 30 - (int)(Math.abs(Math.sin(t * 3f + 1f)) * 5);
                    renderEntity(graphics, entities.get(0), wolfX,    midY, entitySize, mouseX, mouseY);
                    renderEntity(graphics, entities.get(1), creeperX, midY, entitySize, mouseX, mouseY);
                    // Lightning flash: change 4 to adjust flash frequency
                    if ((int)(t * 4) % 2 == 0)
                        graphics.centeredText(this.font, Component.literal("⚡"), midX, midY - 20, 0xFFFFFF00);
                }
            }
            case 2 -> {
                // Hunger Games: 4 mobs slowly rotating in a circle
                int count = Math.min(entities.size(), 4);
                for (int c = 0; c < count; c++) {
                    // Rotation speed: change 0.4f. Circle radius: change 32
                    float angle = (float)(c * Math.PI * 2 / count) + t * 0.4f;
                    renderEntity(graphics, entities.get(c),
                            midX + (int)(Math.cos(angle) * 32),
                            midY + (int)(Math.sin(angle) * 8),
                            entitySize - 4, mouseX, mouseY);
                }
            }
            case 3 -> {
                // Morph Roulette: single entity with white flash on swap
                renderEntity(graphics, entities.getFirst(), midX, midY, entitySize, mouseX, mouseY);
                float flash = Math.max(0f, 1f - rouletteTimer * 3f);
                if (flash > 0.01f)
                    graphics.fill(cardX + 2, sceneTop, cardX + cardW - 2, sceneBottom,
                            ((int)(flash * 200) << 24) | 0xFFFFFF);
            }
            case 4 -> {
                // Morph Trivia: villager with 3 question marks orbiting around it
                renderEntity(graphics, entities.getFirst(), midX, midY, entitySize, mouseX, mouseY);
                // Orbit speed: change 1.2f. Orbit radius: change 28
                for (int q = 0; q < 3; q++) {
                    float qa = t * 1.2f + q * 2.1f;
                    graphics.centeredText(this.font, Component.literal("?"),
                            midX + (int)(Math.cos(qa) * 28),
                            midY - 15 + (int)(Math.sin(qa * 0.7f) * 6),
                            0xFFCC55FF);
                }
            }
            case 5 -> {
                // Morph Hunt story: a creeper (the "hunter") slowly stalks the prey mob from
                // the left. When it gets close enough, the prey is absorbed (teal flash), and
                // a new prey spawns on the right to be hunted again. The ◎ reticle locks onto
                // the prey and pulses to sell the "target acquired" moment.

                // Prey mob — centered slightly right
                int preyX = midX + 20;
                renderEntity(graphics, entities.getFirst(), preyX, midY, entitySize, mouseX, mouseY);

                // Hunter (you) — stalks from left, closes distance over 1.2s cycle
                // huntTimer goes 0→1.2, so progress goes 0→1
                float huntProgress = huntTimer / 2.0f;
                int hunterStartX = midX - 55;
                int hunterEndX   = midX - 10;
                int hunterX = hunterStartX + (int)((hunterEndX - hunterStartX) * huntProgress);
                var mc5 = Minecraft.getInstance();
                if (mc5.player != null)
                    renderEntity(graphics, mc5.player, hunterX, midY, entitySize - 2, mouseX, mouseY);

                // Teal absorb flash when hunter reaches prey (last 0.2s of cycle)
                float huntFlash = Math.max(0f, 1f - (2.0f - huntTimer) * 8f);
                if (huntFlash > 0.01f)
                    graphics.fill(preyX - 14, midY - 28, preyX + 14, midY + 2,
                            ((int)(huntFlash * 180) << 24) | 0x55FFCC);

                // Pulsing ◎ reticle locked on prey — pulses faster as hunter closes in
                float pulseSpeed = 2f + huntProgress * 4f;
                float pulse = (float)(Math.sin(t * pulseSpeed) * 0.5 + 0.5);
                int reticleColor = withAlpha((int)(120 + pulse * 135));
                graphics.centeredText(this.font, Component.literal("◎"), preyX, midY - 18, reticleColor);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Renders a single living entity centered at (x, y) with the given size */
    private void renderEntity(GuiGraphicsExtractor graphics, LivingEntity entity,
                              int x, int y, int size, int mouseX, int mouseY) {
        if (size < 3) return;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics, x - size, y - size * 2, x + size, y,
                    size, 0.0625f, mouseX, mouseY, entity);
        } catch (Exception ignored) {}
    }

    /**
     * Word-wraps a multi-line string to fit within maxWidth pixels.
     * Used by the bottom row description renderer.
     * Returns a flat list of display lines in top→bottom order.
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n")) {
            String remaining = line;
            while (this.font.width(remaining) > maxWidth) {
                int cutAt = remaining.length() - 1;
                while (cutAt > 0 && this.font.width(remaining.substring(0, cutAt)) > maxWidth) cutAt--;
                int spaceAt = remaining.lastIndexOf(' ', cutAt);
                if (spaceAt > 0) cutAt = spaceAt;
                result.add(remaining.substring(0, cutAt).trim());
                remaining = remaining.substring(cutAt).trim();
            }
            result.add(remaining);
        }
        return result;
    }

    private static int withAlpha(int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (5636044 & 0x00FFFFFF);
    }

    /** Linear interpolation between two ARGB colors */
    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int)(ar + (br - ar) * t) << 16)
                | ((int)(ag + (bg - ag) * t) << 8)
                |  (int)(ab + (bb - ab) * t);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        var mc = Minecraft.getInstance();
        if (discSound != null) {
            mc.getSoundManager().stop(discSound);
            discSound = null;
        }
        discStarted = false;
        super.onClose();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Check if any expanded Play button was clicked
        int cardW = (this.width - 20 - CARD_SPACING * (COLUMNS - 1)) / COLUMNS;
        int startX = (this.width - (cardW * COLUMNS + CARD_SPACING * (COLUMNS - 1))) / 2;
        int topRowAnchor    = 52;
        int bottomRowAnchor = this.height - 10;

        boolean hoveringBottom = false;
        for (int i = 0; i < 6; i++)
            if (wasHovered[i] && i >= 3) {
                hoveringBottom = true;
                break;
            }
        int[] clickOrder = hoveringBottom ? new int[]{3,4,5,0,1,2} : new int[]{0,1,2,3,4,5};
        for (int idx = 0; idx < 6; idx++) {
            int i = clickOrder[idx];
            if (hoverProgress[i] < 0.8f) continue;
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = startX + col * (cardW + CARD_SPACING);
            boolean isBottomRow = row == 1;
            int cardH = CARD_HEIGHT_COLLAPSED + (int)(hoverProgress[i] * (CARD_HEIGHT_EXPANDED - CARD_HEIGHT_COLLAPSED));
            int y = isBottomRow ? bottomRowAnchor - cardH : topRowAnchor;

            int btnW = 60, btnH = 14;
            int btnX = x + cardW / 2 - btnW / 2;
            int btnY;

            String desc = EXPANDED_DESC[i];
            if (isBottomRow) {
                // match the btnY calculation from renderReveal bottom row
                List<String> wrapped = wrapText(desc, cardW - 16);
                int titleY = y + cardH - 15;
                int descBottomY = titleY - 4 - wrapped.size() * 10;
                btnY = descBottomY - 22;
            } else {
                // match top row btnY exactly as rendered
                int descY = y + 22;
                for (String line : desc.split("\n")) {
                    String remaining = line;
                    while (this.font.width(remaining) > cardW - 16) {
                        int cutAt = remaining.length() - 1;
                        while (cutAt > 0 && this.font.width(remaining.substring(0, cutAt)) > cardW - 16) cutAt--;
                        int spaceAt = remaining.lastIndexOf(' ', cutAt);
                        if (spaceAt > 0) cutAt = spaceAt;
                        descY += 10;
                        remaining = remaining.substring(cutAt).trim();
                    }
                    descY += 10;
                }
                btnY = descY + 8;
            }

            if (event.x() >= btnX && event.x() < btnX + btnW && event.y() >= btnY && event.y() < btnY + btnH) {
                if (i == 4) {
                    // Morph Trivia — solo and multiplayer
                    this.onClose();
                    Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.TRIVIA));
                    GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0);
                } else if (i == 3) {
                    // Morph Roulette — solo only
                    this.onClose();
                    Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.ROULETTE));
                    GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0);
                } else if (i == 1) {
                    // Mob Brawl — multiplayer
                    this.onClose();
                    Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.MOB_BRAWL));
                    GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0);
                }
                // other game modes coming soon
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}