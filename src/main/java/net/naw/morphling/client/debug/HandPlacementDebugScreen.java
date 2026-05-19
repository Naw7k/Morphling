package net.naw.morphling.client.debug;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.config.HandPlacementConfig;
import org.jspecify.annotations.NonNull;

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
                    _ -> {
                        selectedMob = mob;
                        rebuild();
                    }
            ).bounds(x, y, btnWidth, 20).build());
        }

        // Place axis sliders below the last mob-button row.
        // Last row's bottom edge = 30 + (rows - 1) * 24 + 20.
        int mobRowsBottom = 30 + (rows - 1) * 24 + 20;
        int sliderY = mobRowsBottom + 12;

        // X / Y / Z adjustment buttons
        addAxisButtons("X", sliderY);
        addAxisButtons("Y", sliderY + 30);
        addAxisButtons("Z", sliderY + 60);

        // Reset to defaults button
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset This Mob"),
                _ -> HandPlacementConfig.resetToDefault(selectedMob)
        ).bounds(this.width / 2 - 50, sliderY + 100, 100, 20).build());

        // Close/Back
        this.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> net.minecraft.client.Minecraft.getInstance().setScreen(new DebugScreen())
        ).bounds(this.width / 2 - 82, this.height - 28, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("× Close"),
                _ -> this.onClose()
        ).bounds(this.width / 2 + 2, this.height - 28, 80, 20).build());
    }

    private void addAxisButtons(String axis, int y) {
        int centerX = this.width / 2;

        // - button
        this.addRenderableWidget(Button.builder(
                Component.literal("-"),
                _ -> adjust(axis, -STEP)
        ).bounds(centerX - 80, y, 30, 20).build());

        // + button
        this.addRenderableWidget(Button.builder(
                Component.literal("+"),
                _ -> adjust(axis, STEP)
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
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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

        // Recompute slider Y the same way init() does so the value labels line up
        EntityType<?>[] mobs = HandPlacementConfig.getTunableMobs();
        int perRow = 6;
        int rows = (mobs.length + perRow - 1) / perRow;
        int mobRowsBottom = 30 + (rows - 1) * 24 + 20;
        int sliderY = mobRowsBottom + 12;
        int textOffset = 6; // matches the original 80 -> 86 offset

        // X row
        Component xLine = Component.literal(String.format("X: %.3f", o.x));
        int xWidth = this.font.width(xLine);
        graphics.text(this.font, xLine, centerX - xWidth / 2, sliderY + textOffset, 0xFFFFFFFF, false);

        // Y row
        Component yLine = Component.literal(String.format("Y: %.3f", o.y));
        int yWidth = this.font.width(yLine);
        graphics.text(this.font, yLine, centerX - yWidth / 2, sliderY + 30 + textOffset, 0xFFFFFFFF, false);

        // Z row
        Component zLine = Component.literal(String.format("Z: %.3f", o.z));
        int zWidth = this.font.width(zLine);
        graphics.text(this.font, zLine, centerX - zWidth / 2, sliderY + 60 + textOffset, 0xFFFFFFFF, false);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No blur, no dim — see through to game
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}