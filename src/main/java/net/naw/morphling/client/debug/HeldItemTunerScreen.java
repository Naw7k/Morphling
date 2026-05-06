package net.naw.morphling.client.debug;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HeldItemTunerScreen extends Screen {

    private static final float STEP_POS = 0.05F;
    private static final float STEP_SCALE = 0.05F;
    private static final float STEP_ROT = 15.0F;

    private enum Target {
        POPPY("Iron Golem Poppy"),
        BLOCK("Enderman Block");
        final String label;
        Target(String label) { this.label = label; }
    }

    private Target selected = Target.POPPY;

    public HeldItemTunerScreen() {
        super(Component.literal("Held Item Tuner"));
    }

    private HeldItemTuner.Offset current() {
        return selected == Target.POPPY ? HeldItemTuner.poppy : HeldItemTuner.endermanBlock;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // Target switcher
        int btnY = 20;
        int btnW = 120;
        int gap = 6;
        int totalW = btnW * Target.values().length + gap * (Target.values().length - 1);
        int startX = centerX - totalW / 2;
        for (int i = 0; i < Target.values().length; i++) {
            Target t = Target.values()[i];
            int x = startX + i * (btnW + gap);
            boolean active = selected == t;
            Component label = active
                    ? Component.literal(t.label + " •").copy().withStyle(s -> s.withColor(0xFF55FF55).withBold(true))
                    : Component.literal(t.label);
            this.addRenderableWidget(Button.builder(label, b -> {
                selected = t;
                rebuild();
            }).bounds(x, btnY, btnW, 20).build());
        }

        // Axis +/- rows
        int sliderY = 50;
        int rowH = 22;
        addAxisButtons("X",  sliderY + rowH * 0);
        addAxisButtons("Y",  sliderY + rowH * 1);
        addAxisButtons("Z",  sliderY + rowH * 2);
        addAxisButtons("S",  sliderY + rowH * 3);
        addAxisButtons("RX", sliderY + rowH * 4);
        addAxisButtons("RY", sliderY + rowH * 5);
        addAxisButtons("RZ", sliderY + rowH * 6);

        // Reset
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset"),
                b -> {
                    HeldItemTuner.Offset o = current();
                    HeldItemTuner.Offset def = (selected == Target.POPPY)
                            ? new HeldItemTuner.Offset(-0.800F, 1.150F, -0.250F, 1.050F, 345.0F, 0.0F, 60.0F)
                            : new HeldItemTuner.Offset(-0.300F, 1.000F, -0.050F, 1.000F, 0.0F, -45.0F, 90.0F);
                    o.x = def.x; o.y = def.y; o.z = def.z;
                    o.scale = def.scale;
                    o.rotX = def.rotX; o.rotY = def.rotY; o.rotZ = def.rotZ;
                }
        ).bounds(centerX - 50, sliderY + rowH * 7 + 4, 100, 20).build());

        // Close
        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                b -> this.onClose()
        ).bounds(centerX - 40, this.height - 28, 80, 20).build());
    }

    private void addAxisButtons(String axis, int y) {
        int centerX = this.width / 2;
        boolean isScale = axis.equals("S");
        boolean isRot = axis.startsWith("R");
        float step = isScale ? STEP_SCALE : (isRot ? STEP_ROT : STEP_POS);

        this.addRenderableWidget(Button.builder(
                Component.literal("-"),
                b -> adjust(axis, -step)
        ).bounds(centerX - 80, y, 30, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("+"),
                b -> adjust(axis, step)
        ).bounds(centerX + 50, y, 30, 20).build());
    }

    private void adjust(String axis, float delta) {
        HeldItemTuner.Offset o = current();
        switch (axis) {
            case "X"  -> o.x += delta;
            case "Y"  -> o.y += delta;
            case "Z"  -> o.z += delta;
            case "S"  -> o.scale = Math.max(0.05F, o.scale + delta);
            case "RX" -> o.rotX += delta;
            case "RY" -> o.rotY += delta;
            case "RZ" -> o.rotZ += delta;
        }
    }

    private void rebuild() {
        this.clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        Component title = this.title;
        graphics.text(this.font, title, centerX - this.font.width(title) / 2, 6, 0xFFFFFFFF, false);

        HeldItemTuner.Offset o = current();
        int sliderY = 50;
        int rowH = 22;
        int textOffset = 6;
        drawCenteredValue(graphics, "X: "  + fmt(o.x),     sliderY + rowH * 0 + textOffset);
        drawCenteredValue(graphics, "Y: "  + fmt(o.y),     sliderY + rowH * 1 + textOffset);
        drawCenteredValue(graphics, "Z: "  + fmt(o.z),     sliderY + rowH * 2 + textOffset);
        drawCenteredValue(graphics, "Scale: " + fmt(o.scale), sliderY + rowH * 3 + textOffset);
        drawCenteredValue(graphics, "RotX: " + fmtDeg(o.rotX), sliderY + rowH * 4 + textOffset);
        drawCenteredValue(graphics, "RotY: " + fmtDeg(o.rotY), sliderY + rowH * 5 + textOffset);
        drawCenteredValue(graphics, "RotZ: " + fmtDeg(o.rotZ), sliderY + rowH * 6 + textOffset);
    }

    private void drawCenteredValue(GuiGraphicsExtractor graphics, String text, int y) {
        Component c = Component.literal(text);
        int centerX = this.width / 2;
        graphics.text(this.font, c, centerX - this.font.width(c) / 2, y, 0xFFFFFFFF, false);
    }

    private static String fmt(float v) {
        return String.format("%.3f", v);
    }

    private static String fmtDeg(float v) {
        return String.format("%.1f\u00B0", v);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // see-through to game so we can see live changes
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}