package net.naw.morphling.client.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.MorphVariantManager;

public class VariantViewBuilder {

    private static final int TILE_SIZE = 54;
    private static final int TILE_SPACING = 6;
    private static final int PER_ROW = 6;
    private static final int TOP_BAR_HEIGHT = 80;

    public interface WidgetAdder {
        <T extends net.minecraft.client.gui.components.AbstractWidget> T add(T widget);
    }

    public static void build(EntityType<?> mobType, int screenWidth, WidgetAdder adder, Runnable onBack, Runnable onSelect) {
        if (mobType == EntityType.PARROT) {
            buildParrot(screenWidth, adder, onBack, onSelect);
        } else if (mobType == EntityType.CAT) {
            buildCat(screenWidth, adder, onBack, onSelect);
        } else if (mobType == EntityType.WOLF) {
            buildWolf(screenWidth, adder, onBack, onSelect);
        } else if (mobType == EntityType.COW) {
            buildCow(screenWidth, adder, onBack, onSelect);
        } else if (mobType == EntityType.SHEEP) {
            buildSheep(screenWidth, adder, onBack, onSelect);
        }
    }

    private static int gridStartX(int screenWidth, int count) {
        int cols = Math.min(count, PER_ROW);
        return (screenWidth - (TILE_SIZE * cols + TILE_SPACING * (cols - 1))) / 2;
    }

    private static void addBackButton(WidgetAdder adder, int x, int y, Runnable onBack) {
        adder.add(Button.builder(Component.literal("← Back"), btn -> onBack.run())
                .bounds(x, y, 100, 20).build());
    }

    private static void buildParrot(int screenWidth, WidgetAdder adder, Runnable onBack, Runnable onSelect) {
        var variants = Parrot.Variant.values();
        int startX = gridStartX(screenWidth, variants.length);
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.length; i++) {
            var v = variants[i];
            int x = startX + i * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, startY, TILE_SIZE, EntityType.PARROT,
                    e -> ((net.naw.morphling.mixin.accessors.ParrotVariantAccessor) (Parrot) e).morphling$setVariant(v),
                    MorphVariantManager.getParrotVariant() == v,
                    () -> { MorphVariantManager.setParrotVariant(v); MorphState.setMorph(EntityType.PARROT); onSelect.run(); }
            ));
        }
        addBackButton(adder, startX, startY + TILE_SIZE + 20, onBack);
    }

    private static void buildCat(int screenWidth, WidgetAdder adder, Runnable onBack, Runnable onSelect) {
        var variants = MorphVariantManager.getCatVariantList();
        int startX = gridStartX(screenWidth, Math.min(variants.size(), PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = startX + col * (TILE_SIZE + TILE_SPACING);
            int y = startY + row * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.CAT,
                    e -> ((net.naw.morphling.mixin.accessors.CatVariantAccessor) (Cat) e).morphling$setVariant(v),
                    MorphVariantManager.getCatVariant() == v,
                    () -> { MorphVariantManager.setCatVariant(v); MorphState.setMorph(EntityType.CAT); onSelect.run(); }
            ));
        }
        int rows = (variants.size() + PER_ROW - 1) / PER_ROW;
        addBackButton(adder, startX, startY + rows * (TILE_SIZE + TILE_SPACING) + 10, onBack);
    }

    private static void buildWolf(int screenWidth, WidgetAdder adder, Runnable onBack, Runnable onSelect) {
        var variants = MorphVariantManager.getWolfVariantList();
        int startX = gridStartX(screenWidth, Math.min(variants.size(), PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = startX + col * (TILE_SIZE + TILE_SPACING);
            int y = startY + row * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.WOLF,
                    e -> ((net.naw.morphling.mixin.accessors.WolfVariantAccessor) (Wolf) e).morphling$setVariant(v),
                    MorphVariantManager.getWolfVariant() == v,
                    () -> { MorphVariantManager.setWolfVariant(v); MorphState.setMorph(EntityType.WOLF); onSelect.run(); }
            ));
        }
        int rows = (variants.size() + PER_ROW - 1) / PER_ROW;
        addBackButton(adder, startX, startY + rows * (TILE_SIZE + TILE_SPACING) + 10, onBack);
    }

    private static void buildCow(int screenWidth, WidgetAdder adder, Runnable onBack, Runnable onSelect) {
        var variants = MorphVariantManager.getCowVariantList();
        int startX = gridStartX(screenWidth, variants.size());
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int x = startX + i * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, startY, TILE_SIZE, EntityType.COW,
                    e -> ((Cow) e).setVariant(v),
                    MorphVariantManager.getCowVariant() == v,
                    () -> { MorphVariantManager.setCowVariant(v); MorphState.setMorph(EntityType.COW); onSelect.run(); }
            ));
        }
        addBackButton(adder, startX, startY + TILE_SIZE + 20, onBack);
    }

    private static void buildSheep(int screenWidth, WidgetAdder adder, Runnable onBack, Runnable onSelect) {
        var colors = net.minecraft.world.item.DyeColor.values();
        int startX = gridStartX(screenWidth, Math.min(colors.length, PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < colors.length; i++) {
            var c = colors[i];
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = startX + col * (TILE_SIZE + TILE_SPACING);
            int y = startY + row * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.SHEEP,
                    e -> ((Sheep) e).setColor(c),
                    MorphVariantManager.getSheepColor() == c,
                    () -> { MorphVariantManager.setSheepColor(c); MorphState.setMorph(EntityType.SHEEP); onSelect.run(); }
            ));
        }
        int rows = (colors.length + PER_ROW - 1) / PER_ROW;
        addBackButton(adder, startX, startY + rows * (TILE_SIZE + TILE_SPACING) + 10, onBack);
    }
}