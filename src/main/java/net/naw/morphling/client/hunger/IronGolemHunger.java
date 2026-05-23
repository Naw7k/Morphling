package net.naw.morphling.client.hunger;

import net.minecraft.client.Minecraft;

public class IronGolemHunger {

    private static int savedFoodLevel = -1;

    public static void onMorphToGolem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        savedFoodLevel = mc.player.getFoodData().getFoodLevel();
        mc.player.getFoodData().setFoodLevel(20);
    }

    public static void onUnmorph() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || savedFoodLevel == -1) return;
        mc.player.getFoodData().setFoodLevel(savedFoodLevel);
        savedFoodLevel = -1;
    }
}