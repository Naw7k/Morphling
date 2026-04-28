package net.naw.morphling.client.debug;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.config.HandPlacementConfig;

public class HandPlacementDebugScreen extends Screen {

    private static final float STEP = 0.05F;
    private EntityType<?> selectedMob;

    public HandPlacementDebugScreen() {
        super(Component.literal("Hand Placement Tuner"));
        this.selectedMob = HandPlacementConfig.getTunableMobs()[0];
    }

    @Override
    protected void init() {
        // Mob selection buttons across the top
        EntityType<?>[] mobs = HandPlacementConfig.getTunableMobs();
        int btnWidth = 60;
        int perRow = 6;
        int rowSpacing = 4;
        int rows = (mobs.length + perRow - 1) / perRow;

        for (int i = 0; i < mobs.length; i++) {
            EntityType<?> mob = mobs[i];
            int row = i / perRow;
            int col = i % perRow;
            int rowCount = (row == rows - 1) ? (mobs.length - row * perRow) : perRow;
            int rowWidth = btnWidth * rowCount + (rowCount - 1) * rowSpacing;
            int rowStartX = (this.width - rowWidth) / 2;
            int x = rowStartX + col * (btnWidth + rowSpacing);
            int y = 30 + row * 24;

            boolean isActive = selectedMob == mob;
            Component label = isActive
                    ? Component.literal(mob.getDescription().getString() + " •")
                      .copy().withStyle(style -> style.withColor(0xFF55FF55).withBold(true))
                    : Component.literal(mob.getDescription().getString());

            this.addRenderableWidget(Button.builder(
                    label,
                    btn -> {
                        selectedMob = mob;
                        rebuild();
                    }
            ).bounds(x, y, btnWidth, 20).build());
        }

        // X / Y / Z adjustment buttons
        int sliderY = 80;
        addAxisButtons("X", sliderY, true);
        addAxisButtons("Y", sliderY + 30, false);
        addAxisButtons("Z", sliderY + 60, false);

        // Reset to defaults button
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset This Mob"),
                btn -> HandPlacementConfig.resetToDefault(selectedMob)
        ).bounds(this.width / 2 - 50, sliderY + 100, 100, 20).build());

        // Close
        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.onClose()
        ).bounds(this.width / 2 - 40, this.height - 28, 80, 20).build());
    }

    private void addAxisButtons(String axis, int y, boolean isX) {
        int centerX = this.width / 2;

        // - button
        this.addRenderableWidget(Button.builder(
                Component.literal("-"),
                btn -> adjust(axis, -STEP)
        ).bounds(centerX - 80, y, 30, 20).build());

        // + button
        this.addRenderableWidget(Button.builder(
                Component.literal("+"),
                btn -> adjust(axis, STEP)
        ).bounds(centerX + 50, y, 30, 20).build());
    }

    private void adjust(String axis, float delta) {
        HandPlacementConfig.Offset o = HandPlacementConfig.getOrDefault(selectedMob);
        switch (axis) {
            case "X" -> o.x += delta;
            case "Y" -> o.y += delta;
            case "Z" -> o.z += delta;
        }
        HandPlacementConfig.saveToFile();
    }

    private void rebuild() {
        this.clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // FA indicator at top-right corner
        if (HandPlacementConfig.isFreshAnimationsActive()) {
            Component faTag = Component.literal("FA")
                    .copy().withStyle(style -> style.withColor(0xFF55FFFF));
            int faW = this.font.width(faTag);
            graphics.text(this.font, faTag, this.width - faW - 10, 12, 0xFFFFFFFF, false);
        }

        HandPlacementConfig.Offset o = HandPlacementConfig.getOrDefault(selectedMob);

        // Title at top — centered manually
        Component title = this.title;
        int titleWidth = this.font.width(title);
        graphics.text(this.font, title, (this.width - titleWidth) / 2, 12, 0xFFFFFFFF, false);

        int centerX = this.width / 2;

        // X row — value between the +/- buttons (around y=86)
        Component xLine = Component.literal(String.format("X: %.3f", o.x));
        int xWidth = this.font.width(xLine);
        graphics.text(this.font, xLine, centerX - xWidth / 2, 86, 0xFFFFFFFF, false);

        // Y row
        Component yLine = Component.literal(String.format("Y: %.3f", o.y));
        int yWidth = this.font.width(yLine);
        graphics.text(this.font, yLine, centerX - yWidth / 2, 116, 0xFFFFFFFF, false);

        // Z row
        Component zLine = Component.literal(String.format("Z: %.3f", o.z));
        int zWidth = this.font.width(zLine);
        graphics.text(this.font, zLine, centerX - zWidth / 2, 146, 0xFFFFFFFF, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No blur, no dim — see through to game
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}