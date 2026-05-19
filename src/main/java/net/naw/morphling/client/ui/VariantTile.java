package net.naw.morphling.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public class VariantTile extends AbstractWidget {
    private final LivingEntity previewEntity;
    private final Runnable onClickAction;
    private final boolean isCurrent;
    private final int bgTint; // custom background tint, 0 = default
    private MorphMenuScreen screen;

    public VariantTile(int x, int y, int size, EntityType<?> mobType,
                       java.util.function.Consumer<LivingEntity> applyVariant,
                       boolean isCurrent, Runnable onClickAction) {
        this(x, y, size, mobType, applyVariant, isCurrent, onClickAction, 0);
    }

    public VariantTile(int x, int y, int size, EntityType<?> mobType,
                       java.util.function.Consumer<LivingEntity> applyVariant,
                       boolean isCurrent, Runnable onClickAction, int bgTint) {
        super(x, y, size, size, Component.empty());
        this.isCurrent = isCurrent;
        this.onClickAction = onClickAction;
        this.bgTint = bgTint;

        if (Minecraft.getInstance().screen instanceof MorphMenuScreen morphScreen) {
            this.screen = morphScreen;
        }

        var level = Minecraft.getInstance().level;
        LivingEntity entity = null;
        if (level != null) {
            var created = mobType.create(level, EntitySpawnReason.LOAD);
            if (created instanceof LivingEntity le) {
                entity = le;
                applyVariant.accept(entity);
            }
        }
        this.previewEntity = entity;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (screen != null) {
            if (getY() + height < screen.viewportTop || getY() > screen.viewportTop + screen.viewportHeight) {
                return;
            }
            boolean clip = getY() < screen.viewportTop || getY() + height > screen.viewportTop + screen.viewportHeight;
            if (clip) {
                graphics.enableScissor(getX(), screen.viewportTop, getX() + width, screen.viewportTop + screen.viewportHeight);
            }
            try {
                renderContent(graphics, mouseX, mouseY);
            } finally {
                if (clip) graphics.disableScissor();
            }
        } else {
            renderContent(graphics, mouseX, mouseY);
        }
    }

    private void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hovered = this.isHovered();

        // Use tint if provided, otherwise default colors
        int bgColor;
        if (isCurrent) {
            bgColor = 0xFF2A4D2A;
        } else if (hovered) {
            bgColor = 0xFF4A4A4A;
        } else if (bgTint != 0) {
            bgColor = bgTint;
        } else {
            bgColor = 0xFF2A2A2A;
        }

        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);

        int borderColor = isCurrent ? 0xFF55FF55 : (hovered ? 0xFFAAAAAA : 0xFF555555);
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);

        if (previewEntity != null) {
            int x0 = getX() + 4, y0 = getY() + 4;
            int x1 = getX() + width - 4, y1 = getY() + height - 4;
            float maxDim = Math.max(previewEntity.getBbHeight(), previewEntity.getBbWidth());
            int sz = Math.max(8, (int)(35.0F / Math.max(2F, maxDim)));
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, x0, y0, x1, y1, sz, 0.0625F, mouseX, mouseY, previewEntity
                );
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        onClickAction.run();
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