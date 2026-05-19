package net.naw.morphling.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.naw.morphling.client.core.EntityRegistry;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.MorphVariantManager;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The main morph selection screen (opened with G by default).

 * Layout:
 *  - Top bar: title, current morph status, player face, Reset button
 *  - Category tabs (All / Passive / Hostile / Flying / Aquatic)
 *  - Search box + help (?) + debug + background toggle + two-hands toggle buttons
 *  - Scrollable grid of MorphTile widgets (6 columns)
 *  - Close button at the bottom

 * Two modes:
 *  1. Normal grid view — shows filtered morph tiles
 *  2. Variant view — shows variant tiles for a specific mob (opened by clicking the color icon on a tile)

 * Scrolling:
 *  - Mouse wheel scrolls the grid
 *  - Custom scrollbar on the right side of the grid (draggable)
 *  - Tiles are repositioned on scroll rather than rebuilt (avoids recreating preview entities)

 * Help drawer:
 *  - Slides in from the right when ? is clicked
 *  - Shows keybind hints for each morph
 */
public class MorphMenuScreen extends Screen {

    private static final int TILE_SIZE = 54;
    private static final int TILE_SPACING = 6;
    private static final int COLUMNS = 6;
    private static final int TOP_BAR_HEIGHT = 80;

    // Scrollbar layout constants
    private static final int BOTTOM_PADDING = 40; // space reserved for the Close button
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4; // gap between grid and scrollbar

    // null = normal grid view; non-null = variant selection view for that mob
    private EntityType<?> variantViewMob = null;

    private enum Category {
        ALL("All"), PASSIVE("Passive"), HOSTILE("Hostile"), FLYING("Flying"), AQUATIC("Aquatic");
        final String label;
        Category(String label) { this.label = label; }
    }

    private static final Set<EntityType<?>> HOSTILE_MOBS = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.SPIDER, EntityType.WARDEN,
            EntityType.SLIME
    );

    private static final Set<EntityType<?>> AQUATIC_MOBS = Set.of(
            EntityType.DOLPHIN
    );

    /** Keybind hints shown in the help drawer, ordered by mob. */
    private static final Map<EntityType<?>, String[]> KEYBIND_HINTS = new LinkedHashMap<>();
    static {
        KEYBIND_HINTS.put(EntityType.CHICKEN, new String[]{"Slow fall when jumping"});
        KEYBIND_HINTS.put(EntityType.COW, new String[]{"B = moo"});
        KEYBIND_HINTS.put(EntityType.PIG, new String[]{"B = oink"});
        KEYBIND_HINTS.put(EntityType.SHEEP, new String[]{"R = eat grass (heal + hunger)"});
        KEYBIND_HINTS.put(EntityType.CAT, new String[]{"R = sit", "Shift+R = lie down", "Ctrl+R = relaxed", "Shift+B = hiss", "Ctrl+B = purr"});
        KEYBIND_HINTS.put(EntityType.WOLF, new String[]{"R = sit", "Shift+R = shake", "Ctrl+R = head tilt", "Ctrl+B = pant", "F = angry"});
        KEYBIND_HINTS.put(EntityType.PARROT, new String[]{"R = sit", "Shift+R = dance", "Ctrl+R = imitate nearby mob", "Jump = fly"});
        KEYBIND_HINTS.put(EntityType.ZOMBIE, new String[]{"Hold R at wood door = break it"});
        KEYBIND_HINTS.put(EntityType.SKELETON, new String[]{"R = toggle bow (infinite arrows)"});
        KEYBIND_HINTS.put(EntityType.CREEPER, new String[]{"Hold R = charge explosion"});
        KEYBIND_HINTS.put(EntityType.ENDERMAN, new String[]{"R = teleport", "Shift+R = carry block", "F = angry"});
        KEYBIND_HINTS.put(EntityType.IRON_GOLEM, new String[]{"R = offer flower", "Attack = arm slam + knockback"});
        KEYBIND_HINTS.put(EntityType.DOLPHIN, new String[]{"R = splash jump", "Sprint underwater = speed boost", "B = squeak"});
        KEYBIND_HINTS.put(EntityType.HORSE, new String[]{"R = rear up", "Shift+R = eat grass"});
        KEYBIND_HINTS.put(EntityType.VILLAGER, new String[]{"R = unhappy", "Shift+R = sleep", "Ctrl+R = work sound", "B = hum", "Shift+B = yes", "Ctrl+B = celebrate"});
        KEYBIND_HINTS.put(EntityType.SPIDER, new String[]{"R = leap", "Walk into wall = climb"});
        KEYBIND_HINTS.put(EntityType.SLIME, new String[]{"Space = small hop", "R = big jump", "Contact damage on size 2+"});
        KEYBIND_HINTS.put(EntityType.BEE, new String[]{"Space = toggle flight", "R = sting (poisons, one use)", "Shift+R = roll", "B = pollinate particles + sound", "Shift+B = nectar texture", "F = angry mode"});
    }

    private Category activeCategory = Category.ALL;
    private String searchQuery = "";
    // tiles holds both MorphTile and VariantTile (both extend AbstractWidget)
    private final List<AbstractWidget> tiles = new ArrayList<>();

    private static boolean showBackground = true;
    private boolean helpDrawerOpen = false;
    private float drawerSlide = 0F;
    private static final int DRAWER_WIDTH = 260;
    private int drawerScroll = 0;

    // Scrollbar state
    private int scrollOffset = 0;       // current scroll position in pixels
    private int contentHeight = 0;      // total pixel height of all tile rows
    int viewportHeight = 0;             // visible grid area height (package-private for VariantTile clip checks)
    int viewportTop = 0;                // top Y of the scrollable grid region (package-private for VariantTile)
    private int gridStartXCached = 0;   // cached grid left edge for scrollbar X calculation
    private int gridWidthCached = 0;
    private boolean draggingScrollbar = false;
    private double dragOffsetY = 0;     // mouse Y offset from thumb top when drag started

    public MorphMenuScreen() {
        super(Component.literal("Morphling"));
    }

    @Override
    protected void init() {
        this.tiles.clear();

        // Category tabs
        int tabWidth = 70;
        int tabHeight = 20;
        int tabY = 50;
        int totalTabWidth = tabWidth * Category.values().length + (Category.values().length - 1) * 4;
        int tabStartX = (this.width - totalTabWidth) / 2;

        for (int i = 0; i < Category.values().length; i++) {
            Category cat = Category.values()[i];
            int tabX = tabStartX + i * (tabWidth + 4);
            this.addRenderableWidget(Button.builder(
                    Component.literal(cat.label + (activeCategory == cat ? " •" : "")),
                    _ -> { activeCategory = cat; scrollOffset = 0; rebuild(); }
            ).bounds(tabX, tabY, tabWidth, tabHeight).build());
        }

        // Search box
        int searchWidth = 200;
        int searchX = (this.width - searchWidth) / 2;
        EditBox searchBox = new EditBox(this.font, searchX, tabY + tabHeight + 6, searchWidth, 18,
                Component.literal("Search mobs..."));
        searchBox.setHint(Component.literal("Search mobs..."));
        searchBox.setValue(this.searchQuery);
        searchBox.setResponder(value -> { this.searchQuery = value.toLowerCase(); scrollOffset = 0; rebuildTiles(); });
        this.addRenderableWidget(searchBox);

        // Help (?) button
        int helpBtnSize = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal("?"),
                _ -> { helpDrawerOpen = !helpDrawerOpen; drawerScroll = 0; }
        ).bounds(searchX - helpBtnSize - 6, tabY + tabHeight + 6, helpBtnSize, 18).build());

        // Debug button — opens DebugScreen
        this.addRenderableWidget(Button.builder(
                Component.literal("§c🐛"),
                _ -> Minecraft.getInstance().setScreen(new net.naw.morphling.client.debug.DebugScreen())
        ).bounds(searchX - helpBtnSize - 6 - helpBtnSize - 4, tabY + tabHeight + 6, helpBtnSize, 18).build());

        // Background blur toggle
        int blurBtnSize = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal(showBackground ? "●" : "○"),
                btn -> { showBackground = !showBackground; btn.setMessage(Component.literal(showBackground ? "●" : "○")); }
        ).bounds(searchX + searchWidth + 6, tabY + tabHeight + 6, blurBtnSize, 18).build());

        // Two-hands toggle
        int twoHandsBtnSize = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal(net.naw.morphling.client.config.TwoHandsConfig.isEnabled() ? "✋✋" : "✋"),
                btn -> {
                    boolean newVal = !net.naw.morphling.client.config.TwoHandsConfig.isEnabled();
                    net.naw.morphling.client.config.TwoHandsConfig.setEnabled(newVal);
                    btn.setMessage(Component.literal(newVal ? "✋✋" : "✋"));
                }
        ).bounds(searchX + searchWidth + 6 + blurBtnSize + 4, tabY + tabHeight + 6, twoHandsBtnSize, 18).build());

        // Reset to player button
        int resetBtnWidth = 100;
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset to Player"),
                _ -> { MorphState.reset(); this.onClose(); }
        ).bounds(this.width - resetBtnWidth - 10, 10, resetBtnWidth, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                _ -> this.onClose()
        ).bounds((this.width - 80) / 2, this.height - 28, 80, 20).build());

        // Back button — only shown in variant view
        if (variantViewMob != null) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("← Back"),
                    _ -> closeVariantView()
            ).bounds((this.width - 80) / 2 - 90, this.height - 28, 80, 20).build());
        }

        rebuildTiles();
    }

    /** Full rebuild — clears all widgets and re-runs init(). Used on category/filter change. */
    private void rebuild() {
        this.clearWidgets();
        init();
    }

    /**
     * Rebuilds only the tile widgets, keeping the header/tab/button widgets intact.
     * Used when search query changes or scroll position needs recalculation.
     */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private void rebuildTiles() {
        this.tiles.forEach(this::removeWidget);
        this.tiles.clear();

        int gridWidth = TILE_SIZE * COLUMNS + TILE_SPACING * (COLUMNS - 1);
        int gridStartX = (this.width - gridWidth) / 2;
        int gridStartY = TOP_BAR_HEIGHT + 25;

        // Cache grid dimensions for scrollbar rendering and tile repositioning
        this.gridStartXCached = gridStartX;
        this.gridWidthCached = gridWidth;
        this.viewportTop = gridStartY;
        this.viewportHeight = this.height - BOTTOM_PADDING - viewportTop;

        if (variantViewMob != null) {
            // Variant view: collect VariantTiles from builder and place them in the same grid system
            List<VariantTile> variantTiles = new ArrayList<>();
            List<AbstractWidget> otherWidgets = new ArrayList<>();

            VariantViewBuilder.build(variantViewMob, this.width,
                    new VariantViewBuilder.WidgetAdder() {
                        @Override
                        public <T extends AbstractWidget> T add(T widget) {
                            if (widget instanceof VariantTile vt) {
                                variantTiles.add(vt);
                            } else {
                                otherWidgets.add(widget);
                                MorphMenuScreen.this.addRenderableWidget(widget);
                            }
                            return widget;
                        }
                    },
                    this::onClose
            );

            // Calculate content height for scrollbar
            int rows = (variantTiles.size() + COLUMNS - 1) / COLUMNS;
            this.contentHeight = rows == 0 ? 0 : rows * TILE_SIZE + (rows - 1) * TILE_SPACING;

            int maxScroll = Math.max(0, contentHeight - viewportHeight);
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            if (scrollOffset < 0) scrollOffset = 0;

            // Position variant tiles using same grid logic as main grid
            for (int i = 0; i < variantTiles.size(); i++) {
                VariantTile vt = variantTiles.get(i);
                int col = i % COLUMNS;
                int row = i / COLUMNS;
                int x = gridStartX + col * (TILE_SIZE + TILE_SPACING);
                int y = gridStartY + row * (TILE_SIZE + TILE_SPACING) - scrollOffset;
                vt.setPosition(x, y);
                this.tiles.add(vt);
                this.addRenderableWidget(vt);
            }
            return;
        }

        // Normal grid view
        List<EntityRegistry.MorphEntry> filtered = getFilteredMorphs();

        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        this.contentHeight = rows == 0 ? 0 : rows * TILE_SIZE + (rows - 1) * TILE_SPACING;

        // Clamp scroll in case filter shrank the content
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        for (int i = 0; i < filtered.size(); i++) {
            EntityRegistry.MorphEntry entry = filtered.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = gridStartX + col * (TILE_SIZE + TILE_SPACING);
            int y = gridStartY + row * (TILE_SIZE + TILE_SPACING) - scrollOffset;
            MorphTile tile = new MorphTile(x, y, TILE_SIZE, entry, this);
            this.tiles.add(tile);
            this.addRenderableWidget(tile);
        }
    }

    /**
     * Repositions existing tiles based on current scrollOffset without recreating them.
     * Called on scroll events to avoid rebuilding entity previews every frame.
     */
    private void repositionTiles() {
        int gridStartY = viewportTop;
        for (int i = 0; i < tiles.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = gridStartXCached + col * (TILE_SIZE + TILE_SPACING);
            int y = gridStartY + row * (TILE_SIZE + TILE_SPACING) - scrollOffset;
            tiles.get(i).setPosition(x, y);
        }
    }

    /** Opens the variant selection view for the given mob type. */
    public void openVariantView(EntityType<?> mobType) {
        scrollOffset = 0;
        variantViewMob = mobType;
        rebuild();
    }

    /** Returns to the main grid from the variant selection view. */
    public void closeVariantView() {
        scrollOffset = 0;
        variantViewMob = null;
        rebuild();
    }

    private List<EntityRegistry.MorphEntry> getFilteredMorphs() {
        List<EntityRegistry.MorphEntry> all = EntityRegistry.getAvailableMorphs();
        List<EntityRegistry.MorphEntry> result = new ArrayList<>();
        for (EntityRegistry.MorphEntry entry : all) {
            boolean catMatch = switch (activeCategory) {
                case ALL -> true;
                case PASSIVE -> !HOSTILE_MOBS.contains(entry.type()) && !EntityRegistry.FLYING_MOBS.contains(entry.type()) && !AQUATIC_MOBS.contains(entry.type());
                case HOSTILE -> HOSTILE_MOBS.contains(entry.type());
                case FLYING -> EntityRegistry.FLYING_MOBS.contains(entry.type());
                case AQUATIC -> AQUATIC_MOBS.contains(entry.type());
            };
            if (!catMatch) continue;
            if (!searchQuery.isEmpty()) {
                String name = entry.name().getString().toLowerCase();
                if (!name.contains(searchQuery)) continue;
            }
            result.add(entry);
        }
        return result;
    }

    private boolean needsScrollbar() {
        return contentHeight > viewportHeight;
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Animate help drawer slide
        float target = helpDrawerOpen ? 1F : 0F;
        float diff = target - drawerSlide;
        drawerSlide += Math.signum(diff) * Math.min(Math.abs(diff), 0.15F);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Title and current morph status
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        if (MorphState.isMorphed()) {
            Component current = Component.literal("Currently: " + MorphState.getCurrentMorph().getDescription().getString());
            graphics.centeredText(this.font, current, this.width / 2, 34, 0xAAAAAA);
        } else {
            graphics.centeredText(this.font, Component.literal("Not morphed"), this.width / 2, 34, 0x888888);
        }

        // Player face icon in top-right corner
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerSkin skin = mc.player.getSkin();
            int headSize = 16;
            int headX = this.width - 100 - 10 - headSize - 4;
            int headY = 12;
            PlayerFaceExtractor.extractRenderState(graphics, skin.body().texturePath(), headX, headY, headSize, true, false, -1);
        }

        // Scrollbar rendered on top of tiles but under the help drawer
        if (needsScrollbar()) {
            renderScrollbar(graphics, mouseX, mouseY);
        }

        if (drawerSlide > 0.001F) {
            renderHelpDrawer(graphics);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int trackX = gridStartXCached + gridWidthCached + SCROLLBAR_GAP;
        int trackY = viewportTop;
        int trackH = viewportHeight;

        // Track background
        graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, 0x66000000);

        // Thumb size proportional to viewport/content ratio, minimum 16px
        int thumbH = Math.max(16, (int)((float) viewportHeight / contentHeight * trackH));
        int maxScroll = getMaxScroll();
        int thumbY = maxScroll == 0 ? trackY : trackY + (int)((float) scrollOffset / maxScroll * (trackH - thumbH));

        boolean thumbHovered = mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                && mouseY >= thumbY && mouseY < thumbY + thumbH;
        int thumbColor = (draggingScrollbar || thumbHovered) ? 0xFFAAAAAA : 0xFF777777;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, thumbColor);
    }

    private void renderHelpDrawer(GuiGraphicsExtractor graphics) {
        int drawerW = DRAWER_WIDTH;
        int offset = (int)((1F - drawerSlide) * drawerW);
        int x0 = this.width - drawerW + offset;
        int x1 = this.width + offset;
        int y0 = 0;
        int y1 = this.height;

        // Dim overlay behind the drawer
        int dim = (int)(drawerSlide * 100);
        graphics.fill(0, 0, this.width - drawerW + offset, this.height, (dim << 24));
        graphics.fill(x0, y0, x1, y1, 0xF0101010);
        graphics.fill(x0, y0, x0 + 2, y1, 0xFF55FF55);

        graphics.text(this.font, Component.literal("Keybind Reference"), x0 + 12, 14, 0xFF55FF55, false);
        graphics.text(this.font, Component.literal("Click ? again to close"), x0 + 12, 28, 0xFF888888, false);

        int contentX = x0 + 12;
        int contentY = 50 - drawerScroll;

        graphics.enableScissor(x0, 48, x1, y1 - 30);

        for (Map.Entry<EntityType<?>, String[]> entry : KEYBIND_HINTS.entrySet()) {
            String mobName = entry.getKey().getDescription().getString();
            graphics.text(this.font, Component.literal(mobName), contentX, contentY, 0xFFFFFF55, false);
            contentY += 11;
            for (String line : entry.getValue()) {
                graphics.text(this.font, Component.literal("  " + line), contentX, contentY, 0xFFCCCCCC, false);
                contentY += 10;
            }
            contentY += 6;
        }

        graphics.disableScissor();
        graphics.text(this.font, Component.literal("Scroll to see more"), x0 + 12, y1 - 18, 0xFF666666, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Drawer scrolling takes priority when mouse is over the drawer
        if (helpDrawerOpen && mouseX >= this.width - DRAWER_WIDTH) {
            drawerScroll -= (int)(scrollY * 15);
            if (drawerScroll < 0) drawerScroll = 0;
            int totalHeight = 0;
            for (String[] lines : KEYBIND_HINTS.values()) {
                totalHeight += 11;
                totalHeight += lines.length * 10;
                totalHeight += 6;
            }
            int visibleHeight = this.height - 48 - 30;
            int maxScroll = Math.max(0, totalHeight - visibleHeight);
            if (drawerScroll > maxScroll) drawerScroll = maxScroll;
            return true;
        }

        // Grid scrolling within the viewport area
        if (needsScrollbar()
                && mouseY >= viewportTop && mouseY < viewportTop + viewportHeight) {
            scrollOffset -= (int)(scrollY * 20);
            int maxScroll = getMaxScroll();
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            repositionTiles();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (showBackground) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        // Allow G (or custom key) to close the menu
        if (net.naw.morphling.client.MorphlingClient.openMenuKey.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Close drawer if clicking outside it
        if (helpDrawerOpen) {
            int drawerLeft = this.width - DRAWER_WIDTH;
            if (event.x() < drawerLeft) {
                helpDrawerOpen = false;
                return true;
            }
        }

        // Start scrollbar drag
        if (needsScrollbar()) {
            int trackX = gridStartXCached + gridWidthCached + SCROLLBAR_GAP;
            int trackY = viewportTop;
            int trackH = viewportHeight;
            int thumbH = Math.max(16, (int)((float) viewportHeight / contentHeight * trackH));
            int maxScroll = getMaxScroll();
            int thumbY = maxScroll == 0 ? trackY : trackY + (int)((float) scrollOffset / maxScroll * (trackH - thumbH));

            double mx = event.x();
            double my = event.y();
            if (mx >= trackX && mx < trackX + SCROLLBAR_WIDTH && my >= trackY && my < trackY + trackH) {
                draggingScrollbar = true;
                if (my >= thumbY && my < thumbY + thumbH) {
                    dragOffsetY = my - thumbY;
                } else {
                    // Click on track outside thumb — jump to that position
                    dragOffsetY = thumbH / 2.0;
                    updateScrollFromMouse(my, thumbH, trackY, trackH);
                    repositionTiles();
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.@NonNull MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingScrollbar && needsScrollbar()) {
            int trackY = viewportTop;
            int trackH = viewportHeight;
            int thumbH = Math.max(16, (int)((float) viewportHeight / contentHeight * trackH));
            updateScrollFromMouse(event.y(), thumbH, trackY, trackH);
            repositionTiles();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    private void updateScrollFromMouse(double mouseY, int thumbH, int trackY, int trackH) {
        double thumbTop = mouseY - dragOffsetY;
        double trackRange = trackH - thumbH;
        if (trackRange <= 0) return;
        double ratio = (thumbTop - trackY) / trackRange;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        scrollOffset = (int)(ratio * getMaxScroll());
    }

    /**
     * A single tile in the morph selection grid.

     * Renders a live entity preview, border, and scrolling name label.
     * Clicking the small color icon (top-right) opens the variant view for that mob.
     * Clicking anywhere else applies the morph and closes the screen.
     */
    public static class MorphTile extends AbstractWidget {
        private final EntityRegistry.MorphEntry entry;
        private final MorphMenuScreen screen;
        private LivingEntity previewEntity;

        // Name label scrolling — animates back and forth when text is too wide for the tile
        private static final int SCROLL_PAUSE_FRAMES = 40;
        private static final float SCROLL_SPEED = 0.03F;
        private float scrollOffset = 0F;
        private int pauseCounter = SCROLL_PAUSE_FRAMES;
        private boolean scrollingForward = true;

        public MorphTile(int x, int y, int size, EntityRegistry.MorphEntry entry, MorphMenuScreen screen) {
            super(x, y, size, size, entry.name());
            this.entry = entry;
            this.screen = screen;
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var created = entry.type().create(level, EntitySpawnReason.LOAD);
                if (created instanceof LivingEntity le) {
                    this.previewEntity = le;
                }
            }
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // Skip tiles fully outside the viewport (performance + clean look)
            if (getY() + height < screen.viewportTop || getY() > screen.viewportTop + screen.viewportHeight) {
                return;
            }

            // Clip partially-visible tiles at the top/bottom viewport edges
            boolean clip = getY() < screen.viewportTop || getY() + height > screen.viewportTop + screen.viewportHeight;
            if (clip) {
                graphics.enableScissor(getX(), screen.viewportTop, getX() + width, screen.viewportTop + screen.viewportHeight);
            }

            try {
                renderTileContent(graphics, mouseX, mouseY);
            } finally {
                if (clip) {
                    graphics.disableScissor();
                }
            }
        }

        private void renderTileContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            boolean isCurrent = MorphState.getCurrentMorph() == entry.type();
            boolean hovered = this.isHovered();

            int bgColor = isCurrent ? 0xFF2A4D2A : (hovered ? 0xFF4A4A4A : 0xFF2A2A2A);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);

            int borderColor = isCurrent ? 0xFF55FF55 : (hovered ? 0xFFAAAAAA : 0xFF555555);
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);

            if (previewEntity != null) {
                if (previewEntity instanceof net.minecraft.world.entity.animal.parrot.Parrot pp) {
                    ((net.naw.morphling.mixin.accessors.ParrotVariantAccessor) pp).morphling$setVariant(MorphVariantManager.getParrotVariant());
                }
                int x0 = getX() + 4, y0 = getY() + 4;
                int x1 = getX() + width - 4, y1 = getY() + height - 14;
                float maxDim = Math.max(previewEntity.getBbHeight(), previewEntity.getBbWidth());
                int size = Math.max(8, (int)(35.0F / Math.max(2F, maxDim)));
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(
                            graphics, x0, y0, x1, y1, size, 0.0625F, mouseX, mouseY, previewEntity
                    );
                } catch (Exception ignored) {}
            }

            drawScrollingName(graphics, isCurrent);

            // Variant color icon (top-right corner) — only for mobs with variants
            if (MorphVariantManager.hasVariants(entry.type())) {
                int iconX = getX() + width - 14;
                int iconY = getY() + 3;
                boolean iconHovered = mouseX >= iconX && mouseX < iconX + 11
                        && mouseY >= iconY && mouseY < iconY + 11;
                int bg1 = iconHovered ? 0xFF555555 : 0xFF1A1A1A;
                int bg2 = iconHovered ? 0xFF777777 : 0xFF333333;
                graphics.fill(iconX, iconY, iconX + 11, iconY + 11, bg1);
                graphics.fill(iconX + 1, iconY + 1, iconX + 10, iconY + 10, bg2);
                graphics.fill(iconX + 2, iconY + 2, iconX + 5, iconY + 5, 0xFFFF5555);
                graphics.fill(iconX + 6, iconY + 2, iconX + 9, iconY + 5, 0xFF5555FF);
                graphics.fill(iconX + 2, iconY + 6, iconX + 5, iconY + 9, 0xFF55FF55);
                graphics.fill(iconX + 6, iconY + 6, iconX + 9, iconY + 9, 0xFFFFFF55);
            }
        }

        /** Draws the mob name below the preview, scrolling horizontally if too long. */
        private void drawScrollingName(GuiGraphicsExtractor graphics, boolean isCurrent) {
            var font = Minecraft.getInstance().font;
            String fullName = entry.name().getString();
            int textWidth = font.width(fullName);
            int availableWidth = width - 6;
            int textColor = isCurrent ? 0xFFFFFF55 : 0xFFFFFFFF;
            int textY = getY() + height - 10;

            if (textWidth <= availableWidth) {
                graphics.centeredText(font, entry.name(), getX() + width / 2, textY, textColor);
                return;
            }

            int maxScroll = textWidth - availableWidth;
            if (pauseCounter > 0) { pauseCounter--; }
            else {
                if (scrollingForward) {
                    scrollOffset += SCROLL_SPEED;
                    if (scrollOffset >= maxScroll) { scrollOffset = maxScroll; scrollingForward = false; pauseCounter = SCROLL_PAUSE_FRAMES; }
                } else {
                    scrollOffset -= SCROLL_SPEED;
                    if (scrollOffset <= 0) { scrollOffset = 0; scrollingForward = true; pauseCounter = SCROLL_PAUSE_FRAMES; }
                }
            }

            int clipX0 = getX() + 3;
            int clipX1 = getX() + width - 3;
            graphics.enableScissor(clipX0, textY - 2, clipX1, textY + 10);
            graphics.text(font, entry.name(), clipX0 - (int) scrollOffset, textY, textColor, false);
            graphics.disableScissor();
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            // Click on color variant icon → open variant view
            if (MorphVariantManager.hasVariants(entry.type())) {
                double mx = event.x();
                double my = event.y();
                int iconX = getX() + width - 14;
                int iconY = getY() + 3;
                if (mx >= iconX && mx < iconX + 11 && my >= iconY && my < iconY + 11) {
                    screen.openVariantView(entry.type());
                    return;
                }
            }
            // Click anywhere else → apply morph and close
            MorphState.setMorph(entry.type());
            screen.onClose();
        }

        @Override
        public void playDownSound(@NonNull SoundManager soundManager) {
            AbstractWidget.playButtonClickSound(soundManager);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}