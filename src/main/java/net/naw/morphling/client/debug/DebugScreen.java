package net.naw.morphling.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DebugScreen extends Screen {

    public DebugScreen() {
        super(Component.literal("Morphling Debug"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 40;

        this.addRenderableWidget(Button.builder(
                Component.literal("Damage Indicator: " + (DebugSettings.isDamageIndicatorEnabled() ? "ON" : "OFF")),
                btn -> {
                    DebugSettings.setDamageIndicatorEnabled(!DebugSettings.isDamageIndicatorEnabled());
                    btn.setMessage(Component.literal("Damage Indicator: " + (DebugSettings.isDamageIndicatorEnabled() ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Test Speed: " + (DebugSettings.isTestSpeedEnabled() ? "ON" : "OFF")),
                btn -> {
                    DebugSettings.setTestSpeedEnabled(!DebugSettings.isTestSpeedEnabled());
                    btn.setMessage(Component.literal("Test Speed: " + (DebugSettings.isTestSpeedEnabled() ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, y + 25, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                btn -> this.onClose()
        ).bounds(centerX - 100, y + 100, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Speedometer: " + (DebugSettings.isSpeedometerEnabled() ? "ON" : "OFF")),
                btn -> {
                    DebugSettings.setSpeedometerEnabled(!DebugSettings.isSpeedometerEnabled());
                    btn.setMessage(Component.literal("Speedometer: " + (DebugSettings.isSpeedometerEnabled() ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, y + 50, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Hand Placement Tuner"),
                btn -> Minecraft.getInstance().setScreen(new HandPlacementDebugScreen())
        ).bounds(centerX - 100, y + 75, 200, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}