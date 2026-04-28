package net.naw.morphling.client.health;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;

public class HealthSync {

    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("morphling", "morph_health");
    private static final int TRANSITION_TICKS = 20; // 2 seconds

    private static float savedRatio = 1.0F;
    private static boolean wasMorphedLastTick = false;
    private static float currentModifier = 0F;
    private static float targetModifier = 0F;
    private static int transitionTicks = 0;

    public static void onMorph(net.minecraft.world.entity.Entity morphEntity) {
        if (!(morphEntity instanceof LivingEntity morph)) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        savedRatio = player.getHealth() / player.getMaxHealth();

        float morphMaxHealth = morph.getMaxHealth();
        if (morphMaxHealth > 20.0F) morphMaxHealth = 20.0F;

        targetModifier = morphMaxHealth - 20.0F;
        transitionTicks = TRANSITION_TICKS;
    }

    public static void onUnmorph() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            savedRatio = player.getHealth() / player.getMaxHealth();
        }
        targetModifier = 0F;
        transitionTicks = TRANSITION_TICKS;
    }

    public static void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        boolean morphedNow = MorphState.isMorphed();
        if (!morphedNow && wasMorphedLastTick) {
            onUnmorph();
        }
        wasMorphedLastTick = morphedNow;

        // Lerp current toward target
        if (transitionTicks > 0) {
            float diff = targetModifier - currentModifier;
            currentModifier += diff / transitionTicks;
            transitionTicks--;
            applyModifier(player);
            player.setHealth(savedRatio * player.getMaxHealth());
            if (transitionTicks == 0) currentModifier = targetModifier;
        } else {
            applyModifier(player);
        }
    }

    private static void applyModifier(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        if (currentModifier != 0) {
            attr.addPermanentModifier(new AttributeModifier(
                    MODIFIER_ID,
                    currentModifier,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }
}