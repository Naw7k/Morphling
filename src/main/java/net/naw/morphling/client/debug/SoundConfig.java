package net.naw.morphling.client.debug;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SoundConfig extends Screen {

    // Morph sound volume multipliers
    public static float stepVolumeMultiplier = 1.0F;
    public static float ambientVolumeMultiplier = 1.0F;

    private final Screen parent;

    public SoundConfig(Screen parent) {
        super(Component.literal("Sound Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = 40;

        // Step sound volume
        this.addRenderableWidget(Button.builder(Component.literal("Step Volume: " + String.format("%.2f", stepVolumeMultiplier)),
                _ -> {}).bounds(x, y, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("- 0.1"), _ -> {
            stepVolumeMultiplier = Math.max(0.0F, Math.round((stepVolumeMultiplier - 0.1F) * 10) / 10.0F);
            this.clearWidgets(); this.init();
        }).bounds(x - 50, y, 45, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+ 0.1"), _ -> {
            stepVolumeMultiplier = Math.min(2.0F, Math.round((stepVolumeMultiplier + 0.1F) * 10) / 10.0F);
            this.clearWidgets(); this.init();
        }).bounds(x + 205, y, 45, 20).build());
        y += 40;

        // Ambient sound volume
        this.addRenderableWidget(Button.builder(Component.literal("Ambient Volume: " + String.format("%.2f", ambientVolumeMultiplier)),
                _ -> {}).bounds(x, y, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("- 0.1"), _ -> {
            ambientVolumeMultiplier = Math.max(0.0F, Math.round((ambientVolumeMultiplier - 0.1F) * 10) / 10.0F);
            this.clearWidgets(); this.init();
        }).bounds(x - 50, y, 45, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+ 0.1"), _ -> {
            ambientVolumeMultiplier = Math.min(2.0F, Math.round((ambientVolumeMultiplier + 0.1F) * 10) / 10.0F);
            this.clearWidgets(); this.init();
        }).bounds(x + 205, y, 45, 20).build());
        y += 60;

        // Back / Close
        this.addRenderableWidget(Button.builder(Component.literal("← Back"),
                _ -> this.minecraft.setScreen(this.parent)
        ).bounds(x - 2, y, 97, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("× Close"),
                _ -> this.onClose()
        ).bounds(x + 103, y, 97, 20).build());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}