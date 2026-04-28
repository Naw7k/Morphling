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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MorphMenuScreen extends Screen {

    private static final int TILE_SIZE = 54;
    private static final int TILE_SPACING = 6;
    private static final int COLUMNS = 6;
    private static final int TOP_BAR_HEIGHT = 80;

    private EntityType<?> variantViewMob = null; // null when not in variant view

    private enum Category {
        ALL("All"), PASSIVE("Passive"), HOSTILE("Hostile"), FLYING("Flying"), AQUATIC("Aquatic");
        final String label;
        Category(String label) { this.label = label; }
    }

    private static final Set<EntityType<?>> HOSTILE_MOBS = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.SPIDER, EntityType.WARDEN
    );

    private static final Set<EntityType<?>> AQUATIC_MOBS = Set.of(
            EntityType.DOLPHIN
    );

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
    }

    private Category activeCategory = Category.ALL;
    private String searchQuery = "";
    private EditBox searchBox;
    private final List<MorphTile> tiles = new ArrayList<>();

    private static boolean showBackground = true;
    private boolean helpDrawerOpen = false;
    private float drawerSlide = 0F;
    private static final int DRAWER_WIDTH = 260;
    private int drawerScroll = 0;

    public MorphMenuScreen() {
        super(Component.literal("Morphling"));
    }

    @Override
    protected void init() {
        this.tiles.clear();

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
                    btn -> { activeCategory = cat; rebuild(); }
            ).bounds(tabX, tabY, tabWidth, tabHeight).build());
        }

        int searchWidth = 200;
        int searchX = (this.width - searchWidth) / 2;
        this.searchBox = new EditBox(this.font, searchX, tabY + tabHeight + 6, searchWidth, 18,
                Component.literal("Search mobs..."));
        this.searchBox.setHint(Component.literal("Search mobs..."));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(value -> { this.searchQuery = value.toLowerCase(); rebuildTiles(); });
        this.addRenderableWidget(this.searchBox);

        int helpBtnSize = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal("?"),
                btn -> { helpDrawerOpen = !helpDrawerOpen; drawerScroll = 0; }
        ).bounds(searchX - helpBtnSize - 6, tabY + tabHeight + 6, helpBtnSize, 18).build());

        int blurBtnSize = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal(showBackground ? "●" : "○"),
                btn -> { showBackground = !showBackground; btn.setMessage(Component.literal(showBackground ? "●" : "○")); }
        ).bounds(searchX + searchWidth + 6, tabY + tabHeight + 6, blurBtnSize, 18).build());

        int resetBtnWidth = 100;
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset to Player"),
                btn -> { MorphState.reset(); this.onClose(); }
        ).bounds(this.width - resetBtnWidth - 10, 10, resetBtnWidth, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.onClose()
        ).bounds((this.width - 80) / 2, this.height - 28, 80, 20).build());

        rebuildTiles();
    }

    private void rebuild() {
        this.clearWidgets();
        init();
    }

    private void rebuildTiles() {
        this.tiles.forEach(this::removeWidget);
        this.tiles.clear();

        if (variantViewMob != null) {
            VariantViewBuilder.build(variantViewMob, this.width,
                    new VariantViewBuilder.WidgetAdder() {
                        @Override
                        public <T extends AbstractWidget> T add(T widget) {
                            return MorphMenuScreen.this.addRenderableWidget(widget);
                        }
                    },
                    () -> closeVariantView(),
                    () -> this.onClose()
            );
            return;
        }

        List<EntityRegistry.MorphEntry> filtered = getFilteredMorphs();

        int gridWidth = TILE_SIZE * COLUMNS + TILE_SPACING * (COLUMNS - 1);
        int gridStartX = (this.width - gridWidth) / 2;
        int gridStartY = TOP_BAR_HEIGHT + 25;

        for (int i = 0; i < filtered.size(); i++) {
            EntityRegistry.MorphEntry entry = filtered.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = gridStartX + col * (TILE_SIZE + TILE_SPACING);
            int y = gridStartY + row * (TILE_SIZE + TILE_SPACING);
            MorphTile tile = new MorphTile(x, y, TILE_SIZE, entry, this);
            this.tiles.add(tile);
            this.addRenderableWidget(tile);
        }
    }

    public void openVariantView(EntityType<?> mobType) {
        variantViewMob = mobType;
        rebuild();
    }

    public void closeVariantView() {
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float target = helpDrawerOpen ? 1F : 0F;
        float diff = target - drawerSlide;
        drawerSlide += Math.signum(diff) * Math.min(Math.abs(diff), 0.15F);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        if (MorphState.isMorphed()) {
            Component current = Component.literal("Currently: " + MorphState.getCurrentMorph().getDescription().getString());
            graphics.centeredText(this.font, current, this.width / 2, 34, 0xAAAAAA);
        } else {
            graphics.centeredText(this.font, Component.literal("Not morphed"), this.width / 2, 34, 0x888888);
        }

        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            PlayerSkin skin = mc.player.getSkin();
            int headSize = 16;
            int headX = this.width - 100 - 10 - headSize - 4;
            int headY = 12;
            PlayerFaceExtractor.extractRenderState(graphics, skin.body().texturePath(), headX, headY, headSize, true, false, -1);
        }

        if (drawerSlide > 0.001F) {
            renderHelpDrawer(graphics, mouseX, mouseY);
        }
    }

    private void renderHelpDrawer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int drawerW = DRAWER_WIDTH;
        int offset = (int)((1F - drawerSlide) * drawerW);
        int x0 = this.width - drawerW + offset;
        int x1 = this.width + offset;
        int y0 = 0;
        int y1 = this.height;

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
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (showBackground) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (net.naw.morphling.client.MorphlingClient.openMenuKey.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (helpDrawerOpen) {
            int drawerLeft = this.width - DRAWER_WIDTH;
            if (event.x() < drawerLeft) {
                helpDrawerOpen = false;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public static class MorphTile extends AbstractWidget {
        private final EntityRegistry.MorphEntry entry;
        private final MorphMenuScreen screen;
        private LivingEntity previewEntity;

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

        public EntityRegistry.MorphEntry getEntry() { return entry; }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
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
            MorphState.setMorph(entry.type());
            screen.onClose();
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            AbstractWidget.playButtonClickSound(soundManager);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}