package net.naw.morphling.client.games.MobBrawl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.EntityRegistry;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Mob Brawl morph selection screen — shown to both players before the fight.

 * Both players pick their morph simultaneously.
 * A 30-second timer counts down — if time runs out, forfeit is sent and
 * both players return to the room browser.

 * Layout:
 *   - "Choose Your Morph" title at top with red accent
 *   - 30s countdown timer below title
 *   - Scrollable grid of morph cards (all available morphs)
 *   - Hovered card expands and shows entity preview
 *   - Selected card highlighted with red accent glow
 *   - "Ready!" button at bottom — locks in choice and sends pick to server
 *   - Opponent status shown at bottom

 * Morph cards:
 *   - 6 columns, rows as needed
 *   - Each card: entity name + entity preview on hover
 *   - Selected card shows ✓ badge
 *   - Cards pulse with red accent color theme
 *   - Grid is scrollable if there are more morphs than fit on screen
 */
public class MobBrawlMorphSelectScreen extends Screen {

    private float localTimer = 30f;

    // ── Morph data ────────────────────────────────────────────────────────────
    private final List<EntityType<?>> morphTypes    = new ArrayList<>();
    private final List<LivingEntity>  morphEntities = new ArrayList<>();

    // ── Selection state ───────────────────────────────────────────────────────
    private EntityType<?> selectedMorph = null;
    private boolean       isReady       = false;

    // ── Animation ─────────────────────────────────────────────────────────────
    private float         phaseTimer = 0f;
    private float         introAlpha = 0f;
    private final float[] cardHover;  // hover progress per card

    // ── Scroll ────────────────────────────────────────────────────────────────
    private int scrollOffset = 0;     // how many pixels scrolled down
    private int maxScroll    = 0;     // computed each frame

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int COLS    = 6;
    private static final int CARD_W  = 52;
    private static final int CARD_H  = 52;
    private static final int CARD_GAP = 6;
    private static final int GRID_TOP = 44; // Y where grid starts
    private static final int SCROLL_SPEED = 16;

    public MobBrawlMorphSelectScreen(String ignoredRoomId, boolean ignoredIsHost) {
        super(Component.literal("Mob Brawl — Choose Your Morph"));

        // Load available morphs
        for (EntityRegistry.MorphEntry entry : EntityRegistry.getAvailableMorphs()) {
            morphTypes.add(entry.type());
        }
        cardHover = new float[morphTypes.size()];
    }

    @Override
    protected void init() {
        // Spawn entity previews for morph cards
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (EntityType<?> type : morphTypes) {
                var e = type.create(level, EntitySpawnReason.LOAD);
                morphEntities.add(e instanceof LivingEntity le ? le : null);
            }
        }

        // Default selection — first morph
        if (!morphTypes.isEmpty()) selectedMorph = morphTypes.getFirst();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        phaseTimer += dt;
        introAlpha = Math.min(1f, introAlpha + dt * 2f);
        localTimer = Math.max(0f, localTimer - dt);

        int alpha = (int)(introAlpha * 255);
        int cx    = this.width / 2;

        // Background
        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        // Animated red scan lines
        for (int i = 0; i < 3; i++) {
            float prog = ((phaseTimer * 0.2f + i * 0.33f) % 1.0f);
            int   la   = (int)(Math.sin(prog * Math.PI) * 4);
            int   lx   = (int)(prog * this.width * 1.5f) - this.width / 4;
            graphics.fill(lx, 0, lx + 60, this.height, (la << 24) | 0xFF5555);
        }

        // ── Title ─────────────────────────────────────────────────────────────
        float titlePulse = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
        int titleColor = withAlpha(0xFF5555, (int)((0.7f + titlePulse * 0.3f) * alpha));
        graphics.centeredText(this.font, Component.literal("⚔ Choose Your Morph ⚔"), cx, 10, titleColor);
        graphics.fill(cx - 100, 22, cx + 100, 23, withAlpha(0xFF5555, (int)(introAlpha * 50)));

        // ── Timer ─────────────────────────────────────────────────────────────
        int secsLeft = (int)Math.ceil(localTimer);
        int timerColor = secsLeft <= 5
                ? withAlpha(0xFF5555, 200 + (int)((Math.sin(phaseTimer * 8) * 0.5 + 0.5) * 55))
                : withAlpha(0x888888, alpha);
        graphics.centeredText(this.font, Component.literal("⏱ " + secsLeft + "s to choose"), cx, 28, timerColor);

        // ── Morph grid ────────────────────────────────────────────────────────
        int totalCols = Math.min(COLS, morphTypes.size());
        int rows      = (int)Math.ceil((double)morphTypes.size() / totalCols);
        int gridW     = totalCols * CARD_W + (totalCols - 1) * CARD_GAP;
        int gridX     = cx - gridW / 2;

        // Bottom UI height: ready button + opponent text + padding
        int bottomUiHeight = 20 + 12 + 20 + 12;
        int gridAreaHeight = this.height - GRID_TOP - bottomUiHeight;

        // Compute max scroll
        int totalGridHeight = rows * (CARD_H + CARD_GAP) - CARD_GAP;
        maxScroll = Math.max(0, totalGridHeight - gridAreaHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);

        // Enable scissor so cards don't render outside the grid area
        graphics.enableScissor(0, GRID_TOP, this.width, GRID_TOP + gridAreaHeight);

        for (int idx = 0; idx < morphTypes.size(); idx++) {
            int col   = idx % totalCols;
            int row   = idx / totalCols;
            int cardX = gridX + col * (CARD_W + CARD_GAP);
            int cardY = GRID_TOP + row * (CARD_H + CARD_GAP) - scrollOffset;

            // Skip cards fully outside view
            if (cardY + CARD_H < GRID_TOP || cardY > GRID_TOP + gridAreaHeight) {
                cardHover[idx] = 0f;
                continue;
            }

            EntityType<?> type    = morphTypes.get(idx);
            boolean       selected = type == selectedMorph;
            boolean       hovered  = mouseX >= cardX && mouseX < cardX + CARD_W
                    && mouseY >= cardY && mouseY < cardY + CARD_H
                    && mouseY >= GRID_TOP && mouseY < GRID_TOP + gridAreaHeight;

            // Smooth hover animation
            cardHover[idx] += ((hovered ? 1f : 0f) - cardHover[idx]) * 0.2f;
            float h = cardHover[idx];

            // Card background
            int bg = selected ? withAlpha(0xFF5555, 40) : withAlpha(0x0A0A18, (int)(h * 60 + 30));
            graphics.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, bg);

            // Card border
            int border = selected
                    ? withAlpha(0xFF5555, 180 + (int)((Math.sin(phaseTimer * 3) * 0.5 + 0.5) * 75))
                    : withAlpha(0xFF5555, (int)(h * 80 + 20));
            graphics.fill(cardX,            cardY,            cardX + CARD_W, cardY + 1,      border);
            graphics.fill(cardX,            cardY + CARD_H-1, cardX + CARD_W, cardY + CARD_H, border);
            graphics.fill(cardX,            cardY,            cardX + 1,      cardY + CARD_H, border);
            graphics.fill(cardX + CARD_W-1, cardY,            cardX + CARD_W, cardY + CARD_H, border);

            // Entity preview
            LivingEntity entity = idx < morphEntities.size() ? morphEntities.get(idx) : null;
            if (entity != null) {
                int size = (int)(14 * (1f + h * 0.15f));
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(
                            graphics,
                            cardX + CARD_W/2 - size, cardY + CARD_H/2 - size,
                            cardX + CARD_W/2 + size, cardY + CARD_H/2 + size + 4,
                            size, 0.0625f, mouseX, mouseY, entity);
                } catch (Exception ignored) {}
            }

            // Selected checkmark
            if (selected) {
                graphics.centeredText(this.font, Component.literal("✓"),
                        cardX + CARD_W - 6, cardY + 2, withAlpha(0xFF5555, 220));
            }

            // Morph name tooltip on hover
            if (hovered) {
                String name = type.getDescription().getString();
                int    ttW  = this.font.width(name) + 8;
                int    ttX  = cardX + CARD_W/2 - ttW/2;
                int    ttY  = cardY - 14;
                graphics.fill(ttX, ttY, ttX + ttW, ttY + 12, 0xEE0A0A18);
                graphics.fill(ttX, ttY, ttX + ttW, ttY + 1,  withAlpha(0xFF5555, 120));
                graphics.text(this.font, Component.literal("§7" + name), ttX + 4, ttY + 2, 0xFFCCCCCC, false);
            }
        }

        graphics.disableScissor();

        // ── Scroll indicator ──────────────────────────────────────────────────
        if (maxScroll > 0) {
            int thumbH = Math.max(20, (int)((float)gridAreaHeight / (totalGridHeight) * gridAreaHeight));
            int thumbY = GRID_TOP + (int)((float)scrollOffset / maxScroll * (gridAreaHeight - thumbH));
            int trackX = gridX + gridW + 6;
            graphics.fill(trackX, GRID_TOP, trackX + 3, GRID_TOP + gridAreaHeight, withAlpha(0x333355, 180));
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, withAlpha(0xFF5555, 160));

            // Scroll hint arrows
            if (scrollOffset > 0)
                graphics.centeredText(this.font, Component.literal("▲"), trackX + 1, GRID_TOP - 10, withAlpha(0xFF5555, 180));
            if (scrollOffset < maxScroll)
                graphics.centeredText(this.font, Component.literal("▼"), trackX + 1, GRID_TOP + gridAreaHeight + 2, withAlpha(0xFF5555, 180));
        }

        // ── Ready button ──────────────────────────────────────────────────────
        int btnW = 120, btnH = 20;
        int btnX = cx - btnW / 2;
        int btnY = GRID_TOP + gridAreaHeight + 8;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

        String btnLabel  = isReady ? "§8Waiting..." : "✓  Ready!";
        int    btnBg     = isReady ? withAlpha(0x333333, 180) : (btnHov ? withAlpha(0xFF5555, 80) : withAlpha(0xFF5555, 30));
        int    btnBorder = isReady ? withAlpha(0x444444, 180) : withAlpha(0xFF5555, btnHov ? 220 : 120);

        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
        graphics.fill(btnX,           btnY,          btnX + btnW, btnY + 1,      btnBorder);
        graphics.fill(btnX,           btnY + btnH-1, btnX + btnW, btnY + btnH,   btnBorder);
        graphics.fill(btnX,           btnY,          btnX + 1,    btnY + btnH,   btnBorder);
        graphics.fill(btnX + btnW-1,  btnY,          btnX + btnW, btnY + btnH,   btnBorder);
        graphics.centeredText(this.font, Component.literal(btnLabel), cx, btnY + 6,
                isReady ? 0xFF555555 : (btnHov ? 0xFFFF7777 : 0xFFFF5555));

        // ── Opponent status ───────────────────────────────────────────────────
        graphics.centeredText(this.font, Component.literal("§8Opponent is choosing..."),
                cx, btnY + btnH + 8, withAlpha(0x444455, alpha));

        // ── Selected morph name ───────────────────────────────────────────────
        if (selectedMorph != null) {
            graphics.centeredText(this.font,
                    Component.literal("§7Selected: §f" + selectedMorph.getDescription().getString()),
                    cx, btnY - 12, withAlpha(0x888888, alpha));
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (isReady) return true; // lock input after ready

        int cx        = this.width / 2;
        int totalCols = Math.min(COLS, morphTypes.size());
        int gridW     = totalCols * CARD_W + (totalCols - 1) * CARD_GAP;
        int gridX     = cx - gridW / 2;
        int bottomUiHeight = 20 + 12 + 20 + 12;
        int gridAreaHeight = this.height - GRID_TOP - bottomUiHeight;

        // Check morph card clicks
        for (int idx = 0; idx < morphTypes.size(); idx++) {
            int col   = idx % totalCols;
            int row   = idx / totalCols;
            int cardX = gridX + col * (CARD_W + CARD_GAP);
            int cardY = GRID_TOP + row * (CARD_H + CARD_GAP) - scrollOffset;

            if (event.x() >= cardX && event.x() < cardX + CARD_W
                    && event.y() >= cardY && event.y() < cardY + CARD_H
                    && event.y() >= GRID_TOP && event.y() < GRID_TOP + gridAreaHeight) {
                selectedMorph = morphTypes.get(idx);
                playClick();
                return true;
            }
        }

        // Check ready button
        int btnW = 120, btnH = 20;
        int btnX = cx - btnW / 2;
        int btnY = GRID_TOP + gridAreaHeight + 8;

        if (event.x() >= btnX && event.x() < btnX + btnW
                && event.y() >= btnY && event.y() < btnY + btnH
                && selectedMorph != null) {
            isReady = true;
            MobBrawlClient.sendMorphPick(selectedMorph);
            playReadySound();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll the grid up/down with mouse wheel
        scrollOffset = Math.clamp(
                scrollOffset - (int)(scrollY * SCROLL_SPEED),
                0, maxScroll
        );
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        // Block escape — can't back out of morph select
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();

        // Close screen if session was cleared externally (e.g. server sent phase 0)
        if (MobBrawlClient.getActiveRoomId() == null) {
            Minecraft.getInstance().setScreen(
                    new net.naw.morphling.client.games.ui.RoomBrowserScreen(
                            net.naw.morphling.client.games.ui.MorphGameModeSelect.GameMode.MOB_BRAWL));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void playClick() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.3f, false);
        }
    }

    private void playReadySound() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.5f, false);
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}