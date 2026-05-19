package net.naw.morphling.client.ui;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.sheep.Sheep;
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

    public static void build(EntityType<?> mobType, int screenWidth, WidgetAdder adder, Runnable onSelect) {
        if (mobType == EntityType.PARROT) {
            buildParrot(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.CAT) {
            buildCat(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.WOLF) {
            buildWolf(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.COW) {
            buildCow(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.SHEEP) {
            buildSheep(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.PIG) {
            buildPig(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.CHICKEN) {
            buildChicken(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.HORSE) {
            buildHorse(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.VILLAGER) {
            buildVillager(screenWidth, adder, onSelect);
        } else if (mobType == EntityType.SLIME) {
            buildSlime(screenWidth, adder, onSelect);
        }
    }

    private static int gridStartX(int screenWidth, int count) {
        int cols = Math.min(count, PER_ROW);
        return (screenWidth - (TILE_SIZE * cols + TILE_SPACING * (cols - 1))) / 2;
    }



    private static void buildParrot(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var variants = Parrot.Variant.values();
        int startX = gridStartX(screenWidth, variants.length);
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.length; i++) {
            var v = variants[i];
            int x = startX + i * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, startY, TILE_SIZE, EntityType.PARROT,
                    e -> ((net.naw.morphling.mixin.accessors.ParrotVariantAccessor) e).morphling$setVariant(v),
                    MorphVariantManager.getParrotVariant() == v,
                    () -> { MorphVariantManager.setParrotVariant(v); MorphState.setMorph(EntityType.PARROT); onSelect.run(); }
            ));
        }
        
    }

    private static void buildCat(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var variants = MorphVariantManager.getCatVariantList();
        int startX = gridStartX(screenWidth, Math.min(variants.size(), PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = startX + col * (TILE_SIZE + TILE_SPACING);
            int y = startY + row * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.CAT,
                    e -> ((net.naw.morphling.mixin.accessors.CatVariantAccessor) e).morphling$setVariant(v),
                    MorphVariantManager.getCatVariant() == v,
                    () -> { MorphVariantManager.setCatVariant(v); MorphState.setMorph(EntityType.CAT); onSelect.run(); }
            ));
        }

    }

    private static void buildWolf(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var variants = MorphVariantManager.getWolfVariantList();
        int startX = gridStartX(screenWidth, Math.min(variants.size(), PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = startX + col * (TILE_SIZE + TILE_SPACING);
            int y = startY + row * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.WOLF,
                    e -> ((net.naw.morphling.mixin.accessors.WolfVariantAccessor) e).morphling$setVariant(v),
                    MorphVariantManager.getWolfVariant() == v,
                    () -> { MorphVariantManager.setWolfVariant(v); MorphState.setMorph(EntityType.WOLF); onSelect.run(); }
            ));
        }

    }

    private static void buildCow(int screenWidth, WidgetAdder adder, Runnable onSelect) {
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

    }

    private static void buildSheep(int screenWidth, WidgetAdder adder, Runnable onSelect) {
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

    }

    private static void buildPig(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var variants = MorphVariantManager.getPigVariantList();
        int startX = gridStartX(screenWidth, variants.size());
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int x = startX + i * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, startY, TILE_SIZE, EntityType.PIG,
                    e -> ((net.naw.morphling.mixin.accessors.PigVariantAccessor) e).morphling$setVariant(v),
                    MorphVariantManager.getPigVariant() == v,
                    () -> { MorphVariantManager.setPigVariant(v); MorphState.setMorph(EntityType.PIG); onSelect.run(); }
            ));
        }

    }

    private static void buildChicken(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var variants = MorphVariantManager.getChickenVariantList();
        int startX = gridStartX(screenWidth, variants.size());
        int startY = TOP_BAR_HEIGHT + 25;
        for (int i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            int x = startX + i * (TILE_SIZE + TILE_SPACING);
            adder.add(new VariantTile(x, startY, TILE_SIZE, EntityType.CHICKEN,
                    e -> ((net.minecraft.world.entity.animal.chicken.Chicken) e).setVariant(v),
                    MorphVariantManager.getChickenVariant() == v,
                    () -> { MorphVariantManager.setChickenVariant(v); MorphState.setMorph(EntityType.CHICKEN); onSelect.run(); }
            ));
        }

    }

    private static void buildHorse(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var colors = MorphVariantManager.getHorseColors();
        var markings = MorphVariantManager.getHorseMarkingsList();
        // Show all color+markings combos: 7 colors × 5 markings = 35 tiles
        int total = colors.length * markings.length;
        int startX = gridStartX(screenWidth, Math.min(total, PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;
        int i = 0;
        for (var color : colors) {
            for (var marking : markings) {
                int col = i % PER_ROW, row = i / PER_ROW;
                int x = startX + col * (TILE_SIZE + TILE_SPACING);
                int y = startY + row * (TILE_SIZE + TILE_SPACING);
                boolean selected = MorphVariantManager.getHorseColor() == color
                        && MorphVariantManager.getHorseMarkings() == marking;
                adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.HORSE,
                        e -> ((net.naw.morphling.mixin.accessors.HorseVariantAccessor) e).morphling$setVariantAndMarkings(color, marking),
                        selected,
                        () -> {
                            MorphVariantManager.setHorseColor(color);
                            MorphVariantManager.setHorseMarkings(marking);
                            MorphState.setMorph(EntityType.HORSE);
                            onSelect.run();
                        }
                ));
                i++;
            }
        }
    }

    private static void buildVillager(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        var professions = MorphVariantManager.getVillagerProfessionList();
        var types = MorphVariantManager.getVillagerTypeList();

        int startX = gridStartX(screenWidth, Math.min(professions.size(), PER_ROW));
        int startY = TOP_BAR_HEIGHT + 25;

        // Profession tiles first
        for (int i = 0; i < professions.size(); i++) {
            var v = professions.get(i);
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = startX + col * (TILE_SIZE + TILE_SPACING);
            int y = startY + row * (TILE_SIZE + TILE_SPACING);
            boolean selected = MorphVariantManager.getVillagerProfession() == v;
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.VILLAGER,
                    e -> {
                        if (e instanceof net.minecraft.world.entity.npc.villager.Villager vil)
                            vil.setVillagerData(vil.getVillagerData().withProfession(v));
                    },
                    selected,
                    () -> { MorphVariantManager.setVillagerProfession(v); MorphState.setMorph(EntityType.VILLAGER); onSelect.run(); }
            ));
        }

        // Biome type tiles below
        int professionRows = (professions.size() + PER_ROW - 1) / PER_ROW;
        int typeStartY = startY + professionRows * (TILE_SIZE + TILE_SPACING) + 10;
        int typeStartX = gridStartX(screenWidth, Math.min(types.size(), PER_ROW));

        for (int i = 0; i < types.size(); i++) {
            var v = types.get(i);
            int col = i % PER_ROW, row = i / PER_ROW;
            int x = typeStartX + col * (TILE_SIZE + TILE_SPACING);
            int y = typeStartY + row * (TILE_SIZE + TILE_SPACING);
            boolean selected = MorphVariantManager.getVillagerType() == v;
            adder.add(new VariantTile(x, y, TILE_SIZE, EntityType.VILLAGER,
                    e -> {
                        if (e instanceof net.minecraft.world.entity.npc.villager.Villager vil)
                            vil.setVillagerData(vil.getVillagerData().withType(v));
                    },
                    selected,
                    () -> { MorphVariantManager.setVillagerType(v); MorphState.setMorph(EntityType.VILLAGER); onSelect.run(); },
                    0xFF2A2A3A
            ));
        }
    }

    private static void buildSlime(int screenWidth, WidgetAdder adder, Runnable onSelect) {
        int[] sizes = {1, 2, 4};
        //{"Tiny (1)", "Small (2)", "Big (4)"};
        int startX = gridStartX(screenWidth, sizes.length);
        int startY = TOP_BAR_HEIGHT + 25;

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            int x = startX + i * (TILE_SIZE + TILE_SPACING);
            boolean selected = MorphVariantManager.getSlimeSize() == size;
            adder.add(new VariantTile(x, startY, TILE_SIZE, EntityType.SLIME,
                    e -> {
                        if (e instanceof net.minecraft.world.entity.monster.Slime slime)
                            slime.setSize(size, false);
                    },
                    selected,
                    () -> { MorphVariantManager.setSlimeSize(size); MorphState.setMorph(EntityType.SLIME); onSelect.run(); }
            ));
        }
    }
}